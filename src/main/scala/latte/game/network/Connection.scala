package latte.game.network

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{TimeUnit, _}

import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import latte.game.network.OrderingExecutor._
import latte.game.server.GameException

import scala.concurrent.duration.{Deadline, _}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
 * Created by linyuhe on 2018/9/13.
 */

object Connection {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def newCachedConnectionPool(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty) = new CachedConnectionPool(host, port, listeners)

  def newSingleConnection(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty) = new Connection(host, port, listeners)

}

trait IConnection {

  def ask(cmd: Int, request: MapBean, timeout: Int = 3): MapBean

  def notify(cmd: Int, event: MapBean): Unit

  def close(): Future[Unit]

  protected val closed = new AtomicBoolean(false)

  def isClosed = closed.get()

}

class Connection(val host: String, val port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty) extends IConnection {

  private val channelFuture = connect()

  private val closedPromise = Promise[Unit]()

  private def connect() = {
    val workerGroup = new NioEventLoopGroup()
    val bootstrap = new Bootstrap()
    bootstrap.group(workerGroup)
    bootstrap.channel(classOf[NioSocketChannel])
    bootstrap.option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) // 连接超时
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    bootstrap.handler(new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(new MessageEncoder, new MessageDecoder, new ConnectionOutBoundHandler, new ConnectionInBoundHandler(listeners))
      }
    })
    val channelFuture = bootstrap.connect(host, port)
    // 创建连接关闭时的监听器
    channelFuture.channel().closeFuture().addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        workerGroup.shutdownGracefully()
        closedPromise.trySuccess()
      }
    })
    channelFuture
  }

  // 同时只能发送一个请求
  override def ask(cmd: Int, request: MapBean, timeout: Int) = this.synchronized {
    if (isClosed) throw ConnectionClosedException()
    val channel = channelFuture.sync().channel()
    val msg = Request(cmd, request)
    channel.writeAndFlush(msg).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) msg.promise.tryFailure(future.cause()) // channel异常
      }
    })
    try {
      Await.ready(msg.promise.future, timeout.second).value.get match {
        case Success(response) => response
        case Failure(exception) => throw exception
      }
    } catch {
      case cause: GameException => throw cause // 业务异常
      case cause: Throwable => close(); throw cause // timeout、channel异常
    }
  }

  override def notify(cmd: Int, event: MapBean) = {
    if (isClosed) throw ConnectionClosedException()
    val channel = channelFuture.sync().channel()
    channel.writeAndFlush(Event(cmd, event)).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) close() // channel异常
      }
    })
  }

  override def close() = {
    if (closed.compareAndSet(false, true)) {
      channelFuture.channel().close()
    }
    closedPromise.future
  }
}

class ConnectionOutBoundHandler extends ChannelOutboundHandlerAdapter {

  override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise) = {
    msg match {
      case request: Request => ctx.channel().attr(AttributeKey.valueOf[Request]("request")).set(request)
      case event: Event =>
      case msg: Message => throw UnSupportedMessageException(msg.`type`)
    }
    ctx.write(msg, promise)
  }
}

class ConnectionInBoundHandler(val listeners: Map[Int, MapBean => Any] = Map.empty) extends SimpleChannelInboundHandler[Message] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: Message) = {
    msg match {
      // 正常响应
      case Response(cmd, response) =>
        val request = ctx.channel().attr(AttributeKey.valueOf[Request]("request")).getAndSet(null)
        if (cmd == request.command) request.promise.trySuccess(response)
        else throw CommandNotMatchException(request.command, cmd)
      // 异常响应
      case Exception(cmd, exception) =>
        val request = ctx.channel().attr(AttributeKey.valueOf[Request]("request")).getAndSet(null)
        if (cmd == request.command) request.promise.tryFailure(new GameException(exception))
        else throw CommandNotMatchException(request.command, cmd)
      // 事件
      case Event(cmd, event) =>
        // 并行处理不同类型的事件
        listeners.get(cmd).foreach(listener => orderingExecute[Event](cmd, listener(event)))
      // 请求
      case request: Request => throw UnSupportedMessageException(request.`type`)
    }
  }
}

class CachedConnectionPool(val host: String, port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty) extends IConnection {

  // 同步请求使用连接池
  private val connections = collection.mutable.ListBuffer[(Connection, Deadline)]()
  // 事件使用的connection
  private val eventConnection = Connection.newSingleConnection(host, port, listeners)
  // 定时关闭过期连接
  private val timer = Executors.newSingleThreadScheduledExecutor()

  private val closedPromise = Promise[Unit]()

  timer.scheduleAtFixedRate(new Runnable {
    // 每隔5秒检查一次
    override def run(): Unit = connections.synchronized {
      if (connections.nonEmpty)
        while (connections.last._2.isOverdue()) {
          connections.remove(connections.size - 1)._1.close()
        }
    }
  }, 5, 5, TimeUnit.SECONDS)

  def ask(cmd: Int, request: MapBean, timeout: Int): MapBean = {
    if (isClosed) throw ConnectionClosedException()
    val connection = connections.synchronized {
      if (connections.isEmpty)
        Connection.newSingleConnection(host, port, listeners) // 创建
      else
        connections.remove(0)._1 // 从空闲连接池中拿一个连接
    }
    try {
      connection.ask(cmd, request, timeout) // 发送请求
    } finally {
      if (!connection.isClosed) connections.synchronized {
        connections.insert(0, (connection, 1.minute.fromNow)) // 回收, 1分钟后销毁
      }
    }
  }

  def notify(cmd: Int, event: MapBean): Unit = {
    if (isClosed) throw ConnectionClosedException()
    eventConnection.notify(cmd, event)
  }

  def close() = {
    if (closed.compareAndSet(false, true)) {
      timer.shutdownNow()
      val count = new AtomicInteger()
      val all = eventConnection :: connections.synchronized(connections.map(_._1)).toList
      count.set(all.size)
      all.foreach(_.close().onComplete(f => {
        if (count.decrementAndGet() == 0) closedPromise.trySuccess()
      })(Connection.ec))
    }
    closedPromise.future
  }
}

case class ConnectionClosedException() extends RuntimeException("Connection has been closed.")

case class CommandNotMatchException(cmd1: Int, cmd2: Int) extends RuntimeException(s"Commands not match, expected is ${Integer.toHexString(cmd1)} but is ${Integer.toHexString(cmd2)}")

case class UnSupportedMessageException(`type`: Message.Type) extends RuntimeException(s"Unsupported message type:${`type`.id}")
