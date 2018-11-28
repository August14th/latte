package latte.game.server

import java.util.concurrent.atomic.AtomicInteger

import io.netty.channel.Channel
import latte.game.component.Skill
import latte.game.network.{Event, MapBean}
import latte.game.scene._

import scala.collection.mutable

/**
 * Created by linyuhe on 2018/5/19.
 */

object Player {

  private val counts = mutable.Map.empty[String, AtomicInteger]

  private val players = mutable.Map.empty[String, Player]

  def apply[T](playerId: String)(op: Option[Player] => T): T = {
    if (playerId != null && playerId.nonEmpty) {
      // 锁
      val count = counts.synchronized {
        counts.getOrElseUpdate(playerId, new AtomicInteger(0))
      }
      val player = try count.synchronized {
        // 如果内存中没有重新从数据库中加载一次
        players.getOrElseUpdate(playerId, new Player(playerId))
      } catch {
        case _: PlayerNotFoundException => return op(None)
      }
      try {
        // 基于计数的内存管理
        count.incrementAndGet()
        op(Some(player))
      } finally {
        count.decrementAndGet()
      }
    } else op(None)
  }
}

class Player(id: String) extends User(id) {

  // 技能
  lazy val skill = Skill(this)
  // 状态
  lazy val state = Movement(this)
  // 视野
  lazy val view = View(this)

  override def toMapBean = super.toMapBean ++ MapBean("skill" -> skill.toMapBean)

  // 跳转
  def redirect(scene: Scene, pos: Vector2): Unit = {
    scene.enter(this, pos, 0)
  }

  var channel: Channel = _

  def tell(cmd: Int, event: MapBean): Unit = {
    if (channel != null) this.channel.writeAndFlush(Event(cmd, event))
  }

  def login(): MapBean = {
    toMapBean ++ MapBean("lastPos" -> state.getLastPosition)
  }
}

class User(val id: String) {

  loadFromDB()

  private def loadFromDB() = {
    name = "Player" + id
  }

  var name: String = _

  def persist() = {

  }

  def toMapBean = MapBean("playerId" -> id, "playerName" -> name)

}
