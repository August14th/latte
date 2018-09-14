package latte.game.network

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.LengthFieldBasedFrameDecoder

/**
 * Created by linyuhe on 2018/5/19.
 */
class MessageDecoder extends LengthFieldBasedFrameDecoder(102400, 4 + 1, 4, 0, 0) {

  private val mapper = new ObjectMapper()

  protected override def decode(ctx: ChannelHandlerContext, in: ByteBuf) = {
    super.decode(ctx, in) match {
      case bytes: ByteBuf =>
        val command = bytes.readInt()
        val flag = bytes.readByte()
        val size = bytes.readInt()
        val byteArray = new Array[Byte](size)
        bytes.readBytes(byteArray)
        new Message(command, MapBean(mapper.readValue(byteArray, classOf[java.util.HashMap[String, Any]])), flag)
      case _ => null
    }
  }
}
