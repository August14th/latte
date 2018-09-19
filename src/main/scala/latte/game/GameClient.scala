package latte.game

import latte.game.network.{Connection, MapBean}

/**
 * Created by linyuhe on 2018/9/13.
 */
object GameClient extends App {
  // 创建连接
  val client = Connection.newSingleConnection("localhost", 2018)
  // 请求
  var rsp = client.ask(0x0101, MapBean("playerId" -> "10001"))
  println(rsp.getString("name"))
  rsp = client.ask(0x0201, MapBean())

  client.notify(0x0101, MapBean("secret" -> "42"))
  println(rsp.getString("name"))

  Thread.sleep(10000000)
  println("over.")
}
