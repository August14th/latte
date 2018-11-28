package latte.game

import latte.game.command.{Command01, Command02}
import latte.game.event.Event01
import latte.game.network.{Event, Server}
import latte.game.scene.Scene

/**
 * Created by linyuhe on 2018/5/19.
 */

object GameServer extends App {
  // 启动组件
  Array(Scene).foreach(_.start())
  // 启动网络
  val server = new Server(Array(Command01, Command02).flatMap(_.toHandlers).toMap, Array(Event01).flatMap(_.toHandlers).toMap)
  server.listen(2018).sync
}
