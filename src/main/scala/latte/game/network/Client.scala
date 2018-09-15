package latte.game.network

import java.util

import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import latte.game.network.OrderingExecutor._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * Created by linyuhe on 2018/9/13.
 */

object Client {

}

class Client(val handlers: Map[Int, (Channel, MapBean) => MapBean] = Map.empty) {
  // 连接
  private var channel: Channel = _
  // 发送出去的所有请求
  private val queue = new util.LinkedList[Message]()

  def connect(host: String, port: Int) = {
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
      val future = bootstrap.connect(host, port).sync()
      channel = future.channel()
      channel.closeFuture()
    } catch {
      case ex: Throwable => workerGroup.shutdownGracefully(); throw ex
    }
  }

  def ask(cmd: Int, request: MapBean) = {
    val msg = Request(cmd, request)
    channel.writeAndFlush(msg)
    Await.ready(msg.promise.future, Duration.Inf).value.get match {
      case Success(response) => response
      case Failure(ex) => throw new RuntimeException(ex.getMessage)
    }
  }

  def push(cmd: Int, event: MapBean) = {
    channel.writeAndFlush(Event(cmd, event))
  }

  class ClientOutBoundHandler extends ChannelOutboundHandlerAdapter {

    override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise) = {
      val message = msg.asInstanceOf[Message]
      queue.push(message)
      ctx.write(message, promise)
    }
  }

  class ClientInBoundHandler extends SimpleChannelInboundHandler[Message] {

    override def channelRead0(ctx: ChannelHandlerContext, msg: Message) = {
      val channel = ctx.channel()
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
        // 通知
        case Event(cmd, body) =>
          handlers.get(cmd) match {
            case Some(listener) => orderingExecute(channel, listener(channel, body))
            case None =>
          }
        // 请求
        case request: Request => throw new RuntimeException(s"Unsupported message type:${request.`type`}")
      }
    }
  }

}
