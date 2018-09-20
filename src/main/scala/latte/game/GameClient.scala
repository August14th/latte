package latte.game

import latte.game.network.{Connection, MapBean}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Created by linyuhe on 2018/9/13.
 */
object GameClient extends App {
  // 创建连接
  val client = Connection.newCachedConnectionPool("localhost", 2018)
  // 请求
   var rsp = client.ask(0x0101, MapBean("playerId" -> "10001"))
  println(rsp.getString("name"))
  // 推送
  client.notify(0x0102, MapBean("secret" -> "42"))
  // 关闭
  Await.ready(client.close(), Duration.Inf)
  println(rsp.getString("name"))
  println("over.")
}
