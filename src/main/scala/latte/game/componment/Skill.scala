package latte.game.componment

import latte.game.network.MapBean
import latte.game.player.Player
import latte.game.proxy.ProxyFactory

/**
 * Created by linyuhe on 2018/9/14.
 */
object Skill {

  def apply(player: Player): Skill = {
    ProxyFactory.getSyncProxy(new Skill(player))
  }

}

class Skill(val player: Player) {

  def toMapBean = MapBean.empty

}
