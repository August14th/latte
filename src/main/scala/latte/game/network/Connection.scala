package latte.game.network

import java.lang.reflect.InvocationTargetException
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import latte.game.network.Connection.Handler
import latte.game.server.GameException

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Created by linyuhe on 2018/9/13.
 */

object Connection {

  type Handler = (Connection, MapBean) => MapBean

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def client(host: String, port: Int, listeners: Map[Int, Handler] = Map.empty, onClose: => Any = {}) = {
    val workerGroup = new NioEventLoopGroup()
    val bootstrap = new Bootstrap()
    bootstrap.group(workerGroup)
    bootstrap.channel(classOf[NioSocketChannel])
    bootstrap.option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) // 连接超时
    bootstrap.option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    var connection: Connection = null
    bootstrap.handler(new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel) {
        // 处理事件
        val conn = new Connection(ch, listeners, {
          workerGroup.shutdownGracefully()
          onClose
        })
        ch.pipeline().addLast(new MessageEncoder, new MessageDecoder,
          new conn.ConnectionOutBoundHandler, new conn.ConnectionInBoundHandler)
        connection = conn
      }
    })
    // 建立连接
    bootstrap.connect(host, port).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        if (!future.isSuccess) {
          future.cause().printStackTrace()
          workerGroup.shutdownGracefully()
        }
      }
    }).sync()
    connection
  }

  def server(port: Int, handlers: Map[Int, Handler], onClose: => Any = {}) = {

    val bootstrap = new ServerBootstrap
    bootstrap.group(new NioEventLoopGroup, new NioEventLoopGroup)
    bootstrap.channel(classOf[NioServerSocketChannel])

    bootstrap.childHandler(new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel) {
        val conn = new Connection(ch, handlers, onClose)
        ch.pipeline().addLast(new MessageEncoder, new MessageDecoder,
          new conn.ConnectionOutBoundHandler, new conn.ConnectionInBoundHandler)
      }
    })

    bootstrap.option[Integer](ChannelOption.SO_BACKLOG, 128.asInstanceOf[Integer])
    bootstrap.childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
    // 监听
    bootstrap.bind(port).sync()
  }
}

class Connection(private val channel: Channel, val handlers: Map[Int, Handler], onClose: => Any = {}) {

  private val idGenerator = new AtomicInteger(0)

  private val requests = new ConcurrentHashMap[Int, Request].asScala

  var attachment: Any = _

  // 连接关闭时的监听器
  channel.closeFuture().addListener(new ChannelFutureListener {
    override def operationComplete(future: ChannelFuture): Unit = {
      requests.values.foreach(_.response.tryFailure(new RuntimeException("channel closed")))
      Future(onClose)(Connection.ec)
    }
  })

  def ask(cmd: Int, request: MapBean, timeout: Int = 3) = {
    if (!channel.isActive) throw new ConnectionClosedException
    val req = Request(idGenerator.incrementAndGet(), cmd, request)
    channel.writeAndFlush(req).sync()
    try {
      Await.result(req.response.future, timeout.second)
    } catch {
      case cause: GameException => throw cause // 业务异常
      case cause: TimeoutException => requests.remove(req.id); throw cause // timeout
      case cause: Throwable => throw cause // channel异常
    }
  }

  def tell(cmd: Int, event: MapBean) = {
    if (channel.isActive) {
      channel.writeAndFlush(Event(cmd, event))
    }
  }

  def close() = channel.close()

  private val conn = this

  class ConnectionOutBoundHandler extends ChannelOutboundHandlerAdapter {

    override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise) = {
      msg match {
        case request: Request => requests += request.id -> request
        case _ =>
      }
      ctx.write(msg, promise)
    }
  }

  class ConnectionInBoundHandler extends SimpleChannelInboundHandler[Message] {

    override def channelRead0(ctx: ChannelHandlerContext, msg: Message) = {
      msg match {
        case Request(id, cmd, request) => // 请求
          handlers.get(cmd) match {
            case Some(handler) =>
              Future {
                val response = safeHandle(handler, msg.asInstanceOf[Request])
                channel.writeAndFlush(response)
              }(Connection.ec)
            case None =>
              val ex = Exception(id, cmd, s"Command:0x${Integer.toHexString(cmd)} not found")
              channel.writeAndFlush(ex)
          }
        case Response(id, cmd, response) => // 正常响应
          val req = requests.remove(id)
          req match {
            case Some(request) =>
              if (cmd == request.command) request.response.trySuccess(response)
              else throw CommandNotMatchException(request.command, cmd)
            case None =>
              println(s"invalid response, requestId:$id, cmd:${cmd.toHexString}")
          }
        case Exception(id, cmd, error) => // 异常响应
          val req = requests.remove(id)
          req match {
            case Some(request) =>
              if (cmd == request.command) request.response.tryFailure(new GameException(error))
              else throw CommandNotMatchException(request.command, cmd)
            case None =>
              println(s"invalid response, requestId:$id, cmd:${cmd.toHexString}")
          }
        case Event(cmd, event) => // 事件
          handlers.get(cmd) match {
            case Some(handler) =>
              Future(handler(conn, event))(Connection.ec)
            case None => println(s"no handler found, event:${cmd.toHexString}")
          }
      }
    }

    def safeHandle(handler: Handler, request: Request): Message = {
      try {
        val response = handler(conn, request.request)
        Response(request.id, request.cmd, response)
      } catch {
        case e: Throwable => toException(request, e)
      }
    }

    def toException(request: Request, throwable: Throwable): Exception = {
      throwable match {
        case ex: InvocationTargetException =>
          toException(request, ex.getTargetException)
        case ex: GameException =>
          // 业务错误
          Exception(request.id, request.cmd, ex.getMessage)
        case ex: Throwable =>
          // 服务器内部错误
          ex.printStackTrace()
          Exception(request.id, request.cmd, "Internal server exception")
      }
    }

    // 读异常
    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      cause.printStackTrace()
      close()
    }
  }

}

case class ConnectionClosedException() extends RuntimeException("Connection has been closed.")

case class CommandNotMatchException(cmd1: Int, cmd2: Int) extends RuntimeException(s"Commands not match, expected is ${cmd1.toHexString} but is ${cmd2.toHexString}")

case class UnSupportedMessageException(`type`: Message.Type) extends RuntimeException(s"Unsupported message type:${`type`.id}")
