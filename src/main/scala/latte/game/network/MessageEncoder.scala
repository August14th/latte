package latte.game.network

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

import scala.collection.JavaConversions.mutableMapAsJavaMap
/**
 * Created by linyuhe on 2018/5/19.
 */
class MessageEncoder extends MessageToByteEncoder[Message] {

  private val mapper = new ObjectMapper()

  override protected def encode(ctx: ChannelHandlerContext, msg: Message, out: ByteBuf) {
    val body = mapper.writeValueAsBytes(mutableMapAsJavaMap(msg.body))
    // 协议号
    out.writeInt(msg.command)
    // 标志
    out.writeByte(msg.flag)
    // 写长度
    out.writeInt(body.length)
    // 写数据
    out.writeBytes(body)
  }
}
