package latte.game.scene

import latte.game.network.MapBean
import latte.game.server.{Component, ProxyFactory, Manager, Player}

/**
 * Created by linyuhe on 2018/11/23.
 */

object View extends Manager {

  def apply(player: Player): View = {
    ProxyFactory.getSyncProxy(new View(player))
  }
}

class View(player: Player) extends Component(player) {
  //  视野内的所有玩家
  var players = Set.empty[Player]

  private val ViewSize = 3f

  // 根据在场景内的位置更新视野
  def updateView(): Unit = {
    val players = player.state.pos.scene.surroundingPlayers(player, ViewSize)
    val enters = players -- this.players
    val leaves = this.players -- players
    leave(leaves)
    enter(enters)
  }

  // 有玩家进入
  private def enter(others: Set[Player]): Unit = {
    val enters = others -- players
    if (enters.nonEmpty) {
      players = players ++ enters
      player.tell(0x0210, MapBean("enters" -> enters.map(_.id)))
      // 自己进入其他玩家的视野
      enters.foreach(other => other.view.enter(Set(player)))
    }
  }

  // 有玩家离开
  private def leave(others: Set[Player]): Unit = {
    val leaves = others & players
    if (leaves.nonEmpty) {
      this.players = this.players -- leaves
      player.tell(0x0211, MapBean("leaves" -> leaves.map(_.id)))
      // 自己离开其他玩家的视野
      leaves.foreach(other => other.view.leave(Set(player)))
    }
  }

  // 通知视野内的所有玩家
  def tellAllPlayers(cmd: Int, event: MapBean) = players.foreach(_.tell(cmd, event))
}

