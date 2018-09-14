package latte.game

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

class Skill(player: Player) {


}
