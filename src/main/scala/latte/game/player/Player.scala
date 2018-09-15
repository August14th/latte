package latte.game.player

import java.util.concurrent.atomic.AtomicInteger

import io.netty.channel.Channel
import latte.game.componment.{BaseInfo, Skill}
import latte.game.network.{MapBean, Event}

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
      // 如果内存中没有重新从数据库中加载一次
      count.synchronized {
        players.get(playerId) match {
          case None =>
            val player = loadFromDB(playerId) // 尝试从数据库load
            if (player != null) players += playerId -> player
          case _ =>
        }
      }
      // 基于计数的内存管理
      players.get(playerId) match {
        case player: Some[_] =>
          count.incrementAndGet()
          try {
            op(player)
          } finally {
            count.decrementAndGet()
          }
        case None => op(None)
      }
    } else op(None)
  }

  def loadFromDB(playerId: String): Player = {
    new Player(playerId)
  }
}

class Player(val id: String) extends BaseInfo(id){

  lazy val skill = Skill(this)

  override def toMapBean = super.toMapBean ++ MapBean("skill" -> skill.toMapBean)

  var channel: Channel = _

  def push(cmd: Int, event: MapBean): Unit = {
    if (channel != null) this.channel.write(Event(cmd, event))
  }
}
