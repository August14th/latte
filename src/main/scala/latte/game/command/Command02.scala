package latte.game.command

import latte.game.network.MapBean
import latte.game.player.Player

/**
 * Created by linyuhe on 2018/5/19.
 */
object Command02 extends Command {

  def handler01(player: Player, request: MapBean): MapBean = {
    MapBean("name" -> player.name)
  }
}
