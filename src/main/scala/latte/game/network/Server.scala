package latte.game.network

import java.lang.reflect.InvocationTargetException

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import latte.game.network.OrderingExecutor._
import latte.game.server.GameException

/**
 * Created by linyuhe on 2018/5/19.
 */
object Server {

}

class Server(val handlers: Map[Int, (Channel, MapBean) => MapBean]) {

  // 监听
  def listen(port: Int) = {
    val bootstrap = new ServerBootstrap
    bootstrap.group(new NioEventLoopGroup, new NioEventLoopGroup)
    bootstrap.channel(classOf[NioServerSocketChannel])

    bootstrap.childHandler(new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(new MessageEncoder, new MessageDecoder, new ServerHandler)
      }
    })

    bootstrap.option[Integer](ChannelOption.SO_BACKLOG, 128.asInstanceOf[Integer])
    bootstrap.childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    bootstrap.childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
    // 监听
    bootstrap.bind(port)
  }

  class ServerHandler extends SimpleChannelInboundHandler[Message] {

    def toException(cmd: Int, throwable: Throwable): Exception = {
      throwable match {
        case ex: InvocationTargetException =>
          toException(cmd, ex.getTargetException)
        case ex: GameException =>
          // 业务错误
          Exception(cmd, ex.getMessage)
        case ex: Throwable =>
          // 服务器内部错误
          ex.printStackTrace()
          Exception(cmd, "Internal server exception")
      }
    }

    override def channelRead0(ctx: ChannelHandlerContext, msg: Message) = {
      val channel = ctx.channel()
      msg match {
        // 请求
        case Request(cmd, body) =>
          handlers.get(cmd) match {
            // 每个channel的请求顺序执行
            case Some(handler) =>
              orderingExecute(channel, try {
                val response = handler(channel, body)
                ctx.writeAndFlush(Response(cmd, response))
              } catch {
                case throwable: Throwable =>
                  ctx.writeAndFlush(toException(cmd, throwable))
              })
            // 未注册
            case None => ctx.writeAndFlush(Exception(cmd, s"Command:0x${Integer.toHexString(cmd)} not found"))
          }
        case _ => throw new RuntimeException(s"Unsupported message type:${msg.`type`}")
      }
    }
  }

}


