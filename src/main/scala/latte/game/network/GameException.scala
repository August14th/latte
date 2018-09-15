package latte.game.network

/**
 * Created by linyuhe on 2018/5/19.
 */
class GameException(msg: String) extends RuntimeException(msg)

case class CommandNotMatchException(cmd1: Int, cmd2: Int) extends GameException(s"Commands does not match, " +
  s"expected is ${Integer.toHexString(cmd1)} but is ${Integer.toHexString(cmd2)}")

case class UnsupportedMessageException(`type`: Message.Type) extends GameException(s"Unsupported message type:${`type`}")

case class CommandNotFoundException(cmd: Int) extends GameException(s"Command:0x${Integer.toHexString(cmd)} not found")
