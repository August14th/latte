package latte.game.network

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{TimeUnit, _}

import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import latte.game.network.OrderingExecutor._
import latte.game.server.GameException

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Deadline, _}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

/**
 * Created by linyuhe on 2018/9/13.
 */

object Connection {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def newCachedConnectionPool(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty, auth: (Int, MapBean) = null) = new CachedConnectionPool(host, port, listeners, auth)

  def newSingleConnection(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty, auth: (Int, MapBean) = null) = {
    val conn = new Connection(host, port, listeners)
    if (auth != null)
      try {
        conn.ask(auth._1, auth._2)
      } catch {
        case cause: Throwable => conn.close(); throw cause
      }
    conn
  }

}

trait IConnection {

  def ask(cmd: Int, request: MapBean, timeout: Int = 3): MapBean

  def askAsync(cmd: Int, request: MapBean, callback: MapBean => Any, timeout: Int = 3) = Future(callback(ask(cmd, request, timeout)))(Connection.ec)

  def notify(cmd: Int, event: MapBean): Unit

  def close(): Future[Unit]

  protected val closed = new AtomicBoolean(false)

  def isClosed = closed.get()

}

class Connection(val host: String, val port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty) extends IConnection {

  private val closedPromise = Promise[Unit]()

  private var request: Option[Request] = None

  private val channel = connect()

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
    val channel = bootstrap.connect(host, port).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) workerGroup.shutdownGracefully()
      }
    }).sync().channel() // 建立连接
    // 创建连接关闭时的监听器
    channel.closeFuture().addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        request.foreach(_.response.tryFailure(new RuntimeException("channel closed")))
        workerGroup.shutdownGracefully()
        closedPromise.trySuccess()
      }
    })
    channel
  }

  // 同时只能发送一个请求
  override def ask(cmd: Int, request: MapBean, timeout: Int) = this.synchronized {
    if (isClosed) throw ConnectionClosedException()
    val req = Request(cmd, request)
    channel.writeAndFlush(req).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) req.response.tryFailure(future.cause()) // channel异常
      }
    })
    try {
      Await.result(req.response.future, timeout.second)
    } catch {
      case cause: GameException => throw cause // 业务异常
      case cause: Throwable => close(); throw cause // timeout、channel异常
    }
  }

  override def notify(cmd: Int, event: MapBean) = {
    if (isClosed) throw ConnectionClosedException()
    channel.writeAndFlush(Event(cmd, event)).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) close() // channel异常
      }
    })
  }

  override def close() = {
    if (closed.compareAndSet(false, true)) channel.close()
    closedPromise.future
  }

  class ConnectionOutBoundHandler extends ChannelOutboundHandlerAdapter {

    override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise) = {
      msg match {
        case req: Request => request = Some(req)
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
          val req = request.get
          request = None
          if (cmd == req.command) req.response.trySuccess(response)
          else throw CommandNotMatchException(req.command, cmd)
        // 异常响应
        case Exception(cmd, exception) =>
          val req = request.get
          request = None
          if (cmd == req.command) req.response.tryFailure(new GameException(exception))
          else throw CommandNotMatchException(req.command, cmd)
        // 事件
        case Event(cmd, event) =>
          // 并行处理不同类型的事件
          listeners.get(cmd).foreach(listener => orderingExecute[Event](cmd, listener(event)))
        // 请求
        case request: Request => throw UnSupportedMessageException(request.`type`)
      }
    }

    // 读异常
    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      cause.printStackTrace()
      close()
    }
  }

}

class CachedConnectionPool(val host: String, port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty, auth: (Int, MapBean) = null) extends IConnection {

  // 同步请求使用连接池
  private val connections = ListBuffer[(Connection, Deadline)]()
  // 事件使用的connection
  private val eventConnection = Connection.newSingleConnection(host, port, listeners, auth)
  // 定时关闭过期连接
  private val timer = Executors.newSingleThreadScheduledExecutor()

  private val closedPromise = Promise[Unit]()

  timer.scheduleAtFixedRate(new Runnable {
    // 每隔5秒检查一次
    override def run(): Unit = connections.synchronized {
      val timeout = connections.takeWhile(_._2.isOverdue())
      connections.remove(0, timeout.length)
      timeout.foreach(_._1.close())
    }
  }, 5, 5, TimeUnit.SECONDS)

  def ask(cmd: Int, request: MapBean, timeout: Int): MapBean = {
    if (isClosed) throw ConnectionClosedException()
    // 从空闲连接池中拿一个连接，先进后出，类似于栈
    val (last, _) = connections.synchronized(if (connections.nonEmpty) connections.remove(connections.length - 1) else null)
    val connection = if (last != null) last else Connection.newSingleConnection(host, port, listeners, auth)
    try {
      connection.ask(cmd, request, timeout) // 发送请求
    } finally {
      if (!connection.isClosed) connections.synchronized {
        connections.append((connection, 1.minute.fromNow)) // 回收, 1分钟后销毁
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
