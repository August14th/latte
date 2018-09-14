package latte.game.network

import scala.concurrent.Promise

object Message {

}

/**
 * Created by linyuhe on 2018/5/19.
 */
class Message(val command: Int, val body: MapBean, val flag: Byte = 0) {

  def isPush = (flag & 0x0001) == 1

  def isError = (flag >> 1 & 0x0001) == 1

  val promise = Promise[MapBean]()
}

case class Notice(cmd: Int, notice: MapBean) extends Message(cmd, notice, 0x0001)

case class Request(cmd: Int, request: MapBean) extends Message(cmd, request, 0x0000)

case class Response(cmd: Int, response: MapBean) extends Message(cmd, response, 0x0000)

case class ErrorResponse(cmd: Int, error: String) extends Message(cmd, MapBean("err" -> error), 0x0002)
