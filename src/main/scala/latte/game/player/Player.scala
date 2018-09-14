package latte.game.player

import io.netty.channel.Channel
import latte.game.Skill
import latte.game.network.{MapBean, Notice}

import scala.collection.mutable

/**
 * Created by linyuhe on 2018/5/19.
 */

object Player {

  private val locks = mutable.Map.empty[String, AnyRef]

  private val players = mutable.Map.empty[String, Player]

  def apply(playerId: String): Option[Player] = {
    if (playerId.nonEmpty) {
      val lock = locks.synchronized {
        locks.getOrElseUpdate(playerId, new Object)
      }
      lock.synchronized {
        players.get(playerId) match {
          case None =>
            val player = loadFromDB(playerId) // 尝试从数据库load
            if (player != null) players += playerId -> player
          case _ =>
        }
      }
      players.get(playerId)
    } else None
  }

  def loadFromDB(playerId: String): Player = {
    new Player(playerId)
  }
}

class Player(val id: String) {

  lazy val skill = Skill(this)

  def channel: Channel = null

  def persist() = {

  }

  def toMapBean = {
    MapBean("playerId" -> id)
  }

  def push(cmd: Int, notice: MapBean): Unit = {
    // 最低位是push位
    this.channel.write(Notice(cmd, notice))
  }
}
