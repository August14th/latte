package latte.game.bootstrap

import latte.game.command.{Command01, Command02}
import latte.game.network.Server
import latte.game.scene.Scene

/**
 * Created by linyuhe on 2018/5/19.
 */

object GameServer extends App {
  // 启动组件
  Array(Scene).foreach(_.preStart())
  // 启动网络
  val server = new Server(Array(Command01, Command02).flatMap(_.toHandlers).toMap)
  server.listen(2018).sync
}
