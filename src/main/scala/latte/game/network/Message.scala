package latte.game.network

import scala.concurrent.Promise

/**
 * Created by linyuhe on 2018/5/19.
 */

object Message extends Enumeration {
  type Type = Value
  // 消息类型 1请求 2响应 3错误响应 4推送
  val Request = Value(0)
  val Response = Value(1)
  val Exception = Value(2)
  val Event = Value(3)
}

class Message(val command: Int, val body: MapBean, val `type`: Message.Type) {
  val promise = Promise[MapBean]()
}

case class Request(cmd: Int, request: MapBean) extends Message(cmd, request, Message.Request)

case class Response(cmd: Int, response: MapBean) extends Message(cmd, response, Message.Response)

case class Exception(cmd: Int, error: String) extends Message(cmd, MapBean("err" -> error), Message.Exception)

case class Event(cmd: Int, event: MapBean) extends Message(cmd, event, Message.Event)
