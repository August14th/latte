package latte.game.command

import latte.game.network.{Connection, MapBean}
import latte.game.server.{Command, Player, PlayerNotFoundException}

/**
 * Created by linyuhe on 2018/9/13.
 */
object Command01 extends Command {

  // 登录
  def handler01(connection: Connection, request: MapBean): MapBean = {
    val playerId = request.getString("playerId")
    Player(playerId){
      case Some(player) =>
        connection.attachment = playerId
        player.login(connection)
      case None => throw PlayerNotFoundException(playerId)
    }
  }
}
