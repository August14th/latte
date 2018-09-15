package latte.game.bootstrap

import latte.game.network.{Client, MapBean}

/**
 * Created by linyuhe on 2018/9/13.
 */
object GameClient extends App {
  // 创建连接
  val client = new Client()
  client.connect("localhost", 2018)
  // 请求
  var rsp = client.ask(0x0101, MapBean("playerId" -> "10001"))
  println(rsp.getString("name"))
  rsp = client.ask(0x0201, MapBean())
  println(rsp.getString("name"))
}
