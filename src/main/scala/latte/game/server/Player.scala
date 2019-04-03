package latte.game.server

import java.util.concurrent.ConcurrentHashMap

import latte.game.component.Skill
import latte.game.network.{Connection, MapBean}
import latte.game.scene._

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Created by linyuhe on 2018/5/19.
 */

object Player {

  private val locks = mutable.Map.empty[String, Object]

  private val players = new ConcurrentHashMap[String, Player].asScala

  def apply[T](playerId: String)(op: Option[Player] => T): T = {
    if (playerId != null && playerId.nonEmpty) {
      // 顺序处理
      val lock = locks.synchronized {
        locks.getOrElseUpdate(playerId, new Object)
      }
      lock.synchronized {
        val player = players.get(playerId) match {
          case p: Some[Player] => p
          case None => loadPlayer(playerId)
        }
        op(player)
      }
    } else op(None)
  }

  private def loadPlayer(playerId: String): Option[Player] = {
    if(playerId == "10000") Some(new Player(playerId))
    else None
  }

  def newPlayer(playerId: String, name: String): Unit = {

  }
}

class Player(id: String) extends User(id) {
  // 技能
  lazy val skill = Skill(this)
  // 事件
  lazy val eventHub = EventHub(this)
  // 客户端连接
  private var connection: Option[Connection] = None
  // 场景id
  var sceneId: Int = 0

  var speed: Double = 7d

  override def toMapBean = super.toMapBean ++ MapBean("skill" -> skill.toMapBean)

  // 跳转
  def redirect(scene: Scene, pos: Vector2): Unit = {
    scene.enter(this, pos, 0)
  }

  def tell(cmd: Int, event: MapBean): Unit = if (connection.isDefined) connection.get.tell(cmd, event)



  def login(connection: Connection): MapBean = {
    this.connection = Some(connection)
    toMapBean ++ MapBean("lastPos" -> MapBean("sceneId" -> 10001, "x" -> 1730, "z" -> -3800, "angle" -> 0))
  }

  def scene = if (sceneId != 0) Scene(sceneId) else None

  def movement = {
    if (scene.isDefined)
      scene.get.movements.movement(this)
    else None
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
