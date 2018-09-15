package latte.game

/**
 * Created by linyuhe on 2018/5/19.
 */
class GameException(msg: String) extends RuntimeException(msg)

case class PlayerNotFoundException(playerId: String) extends GameException(s"Player:$playerId not found")