package latte.game.network

import scala.concurrent.Promise

/**
 * Created by linyuhe on 2018/5/19.
 */

object Message extends Enumeration {
  type Type = Value
  // 消息类型 0请求 1响应 2异常响应 3事件
  val Request = Value(0)
  val Response = Value(1)
  val Exception = Value(2)
  val Event = Value(3)
}

class Message(val id: Int, val command: Int, val body: MapBean, val `type`: Message.Type) {

}

case class Request(override val id: Int, cmd: Int, request: MapBean) extends Message(id, cmd, request, Message.Request) {
  val response = Promise[MapBean]()
}

case class Response(override val id: Int, cmd: Int, response: MapBean) extends Message(id, cmd, response, Message.Response)

case class Exception(override val id: Int, cmd: Int, error: String) extends Message(id, cmd, MapBean("err" -> error), Message.Exception)

case class Event(cmd: Int, event: MapBean) extends Message(0, cmd, event, Message.Event)
