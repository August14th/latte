package latte.game.network

import java.util
import java.util.concurrent.{Executors, TimeUnit}

import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import latte.game.network.OrderingExecutor._

import scala.concurrent.Await
import scala.concurrent.duration.{Deadline, Duration, _}
import scala.util.{Failure, Success}

/**
 * Created by linyuhe on 2018/9/13.
 */

object Connection {

  def newCachedConnectionPool(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty) = new CachedConnectionPool(host, port, listeners)

  def newSingleConnection(host: String, port: Int, listeners: Map[Int, MapBean => Any] = Map.empty) = new Connection(host, port, listeners)

}

trait IConnection {

  def ask(cmd: Int, request: MapBean): MapBean

  def notify(cmd: Int, event: MapBean): Unit

  def close(): Unit
}

class Connection(val host: String, val port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty) extends IConnection {
  // 发送出去的所有请求
  private val queue = new util.LinkedList[Request]()

  private val channelFuture = connect()

  private def channel = channelFuture.awaitUninterruptibly().channel()

  private def connect() = {
    val workerGroup = new NioEventLoopGroup()
    try {
      val bootstrap = new Bootstrap()
      bootstrap.group(workerGroup)
      bootstrap.channel(classOf[NioSocketChannel])
      bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      bootstrap.handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel) {
          ch.pipeline().addLast(new MessageEncoder, new MessageDecoder, new ClientOutBoundHandler, new ClientInBoundHandler)
        }
      })
      bootstrap.connect(host, port)
    } catch {
      case ex: Throwable => workerGroup.shutdownGracefully(); throw ex
    }
  }

  override def ask(cmd: Int, request: MapBean) = {
    val msg = Request(cmd, request)
    channel.writeAndFlush(msg)
    Await.ready(msg.promise.future, Duration.Inf).value.get match {
      case Success(response) => response
      case Failure(ex) => throw new RuntimeException(ex.getMessage)
    }
  }

  override def notify(cmd: Int, event: MapBean) = {
    channel.writeAndFlush(Event(cmd, event))
  }

  override def close(): Unit = {
    channel.close()
  }

  class ClientOutBoundHandler extends ChannelOutboundHandlerAdapter {

    override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise) = {
      msg match {
        case request: Request => queue.push(request)
        case event: Event =>
        case msg: Message => throw new RuntimeException(s"Unsupported message type:${msg.`type`}")
      }
      ctx.write(msg, promise)
    }
  }

  class ClientInBoundHandler extends SimpleChannelInboundHandler[Message] {

    override def channelRead0(ctx: ChannelHandlerContext, msg: Message) = {
      msg match {
        // 正常响应
        case Response(cmd, body) =>
          val request = queue.poll()
          if (cmd == request.command) request.promise.success(body)
          else throw new RuntimeException(s"Commands not match, " +
            s"expected is ${Integer.toHexString(request.command)} but is ${Integer.toHexString(cmd)}")
        // 异常响应
        case Exception(cmd, errMsg) =>
          val request = queue.poll()
          if (cmd == request.command) request.promise.failure(new RuntimeException(errMsg))
          else throw new RuntimeException(s"Commands not match, " +
            s"expected is ${Integer.toHexString(request.command)} but is ${Integer.toHexString(cmd)}")
        // 事件
        case Event(cmd, body) =>
          // 并行处理不同类型的事件
          listeners.get(cmd).foreach(listener => orderingExecute[Event](cmd, listener(body)))
        // 请求
        case request: Request => throw new RuntimeException(s"Unsupported message type:${request.`type`}")
      }
    }
  }

}

class CachedConnectionPool(val host: String, port: Int, val listeners: Map[Int, MapBean => Any] = Map.empty) extends IConnection {

  // 同步请求使用连接池
  private val idles = collection.mutable.ListBuffer[(Connection, Deadline)]()
  // 事件使用的client
  private val eventConnection = Connection.newSingleConnection(host, port, listeners)
  // 启动定时器
  private val timer = Executors.newSingleThreadScheduledExecutor()

  timer.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = idles.synchronized {
      if (idles.nonEmpty)
        while (idles.last._2.isOverdue()) {
          idles.remove(idles.size - 1)._1.close()
        }
    }
  }, 5, 5, TimeUnit.SECONDS)

  // 每隔5秒检查一次

  def ask(cmd: Int, request: MapBean): MapBean = {
    val connection = idles.synchronized {
      if (idles.isEmpty)
        Connection.newSingleConnection(host, port, listeners) // 创建
      else
        idles.remove(0)._1 // 从空闲连接池中拿一个连接
    }
    try {
      connection.ask(cmd, request) // 操作
    } finally {
      idles.synchronized {
        idles.insert(0, (connection, 1.minute.fromNow)) // 回收, 1分钟后过期
      }
    }
  }

  def notify(cmd: Int, event: MapBean): Unit = eventConnection.notify(cmd, event)

  def close(): Unit = {
    timer.shutdown()
    idles.synchronized(idles.foreach(_._1.close()))
    eventConnection.close()
  }
}
