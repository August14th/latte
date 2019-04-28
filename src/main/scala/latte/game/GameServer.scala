package latte.game

import latte.game.command.{Command01, Command02}
import latte.game.event.Event01
import latte.game.network.Connection
import latte.game.scene.Scene

/**
 * Created by linyuhe on 2018/5/19.
 */

object GameServer extends App {
  // 启动组件
  val managers = Array(Scene)
  // 命令
  val commands = Array(Command01, Command02)
  // 事件
  val events = Array(Event01)
  // 转换成handler
  val handlers = commands.flatMap(_.toHandlers).toMap ++ events.flatMap(_.toHandlers).toMap
  // 启动网络
  Connection.server(2019, handlers)
}