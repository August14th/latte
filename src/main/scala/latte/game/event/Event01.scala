package latte.game.event

import latte.game.network.MapBean
import latte.game.server.{Player, Event}

/**
 * Created by linyuhe on 2018/9/17.
 */
object Event01 extends Event {

  def handler01(player: Player, event: MapBean) = {

    println(s"playerId: ${player.id}, secret:${event.getString("secret")}")

  }

}
