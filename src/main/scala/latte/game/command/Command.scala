package latte.game.command

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import latte.game.network.MapBean
import latte.game.player.Player

/**
 * Created by linyuhe on 2018/5/19.
 */
trait Command {

  def toHandlers = {
    val clazz = this.getClass
    val name = clazz.getName
    val base = Integer.parseInt(name.substring(name.length - 3, name.length - 1), 16) * 256
    clazz.getMethods.collect { case method if method.getName.startsWith("handler") =>

      val cmd = base + Integer.parseInt(method.getName.substring(7, method.getName.length), 16)
      // 生成响应函数
      cmd -> (if (method.getParameterTypes.toList.head == classOf[Player]) {
        // 登录后
        (channel: Channel, request: MapBean) =>
          val player = channel.attr(AttributeKey.valueOf[Player]("player")).get()
          method.invoke(this, player, request).asInstanceOf[MapBean]
      } else {
        // 登录前
        (channel: Channel, request: MapBean) =>
          method.invoke(this, channel, request).asInstanceOf[MapBean]
      })
    }
  }

}
