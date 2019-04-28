package latte.game.component

import latte.game.network.MapBean
import latte.game.server.{Component, Player}

/**
 * Created by linyuhe on 2018/9/14.
 */
object Skill {

  def apply(player: Player): Skill = {
    new Skill(player)
  }

}

class Skill private(player: Player) extends Component(player) {

  def toMapBean = MapBean.empty

}