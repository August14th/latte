package latte.game

import latte.game.network.{Connection, MapBean}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Created by linyuhe on 2018/9/13.
 */
object GameClient extends App {
  // 创建连接
  val client = Connection.client("localhost", 2019)
  // 请求
   var rsp = client.ask(0x0101, MapBean("playerId" -> "10001"))
  println(rsp.toString())
  client.close().sync()
  System.exit(1)
}
