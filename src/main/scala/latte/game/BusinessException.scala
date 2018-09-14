package latte.game

/**
 * Created by linyuhe on 2018/5/19.
 */
case class PlayerNotFoundException(playerId: String) extends RuntimeException(s"Player:$playerId not found")


