package latte.game.server

import latte.game.network.{Connection, MapBean}

/**
 * Created by linyuhe on 2018/9/17.
 */
trait Event {

  def toHandlers = {
    val clazz = this.getClass
    val name = clazz.getName
    val base = Integer.parseInt(name.substring(name.length - 3, name.length - 1), 16) * 256
    clazz.getMethods.collect { case method if method.getName.startsWith("handler") =>

      val cmd = base + Integer.parseInt(method.getName.substring(7, method.getName.length), 16)
      // 生成响应函数
      cmd -> (if (method.getParameterTypes.toList.head == classOf[Player]) {
        // 登录后
        (connection: Connection, request: MapBean) =>
          val playerId = connection.attachment.asInstanceOf[String]
          Player(playerId) {
            case Some(player) =>
              method.invoke(this, player, request)
              MapBean.empty
            case None => throw PlayerNotFoundException(playerId)
          }
      } else {
        // 登录前
        (connection: Connection, request: MapBean) =>
          method.invoke(this, connection, request)
          MapBean.empty
      })
    }
  }
}
