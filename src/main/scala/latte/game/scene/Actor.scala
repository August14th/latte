package latte.game.scene

import latte.game.network.{PlayerChannel, MapBean}
import latte.game.server.Player

import scala.collection.mutable.ListBuffer

/**
 * Created by linyuhe on 2019/4/4.
 */
class Actor(val playerId: String, val scene: Scene, var pos: Vector2, var towards: Vector2) {

  var views = Set.empty[Actor]

  var speed = 6f

  var viewSize = 10f

  var state = MoveState(0)

  def move(event: MoveEvent): Unit = {
    this.pos = event.pos
    this.towards = event.towards
    val state = event.state
    if (state.`state` == 2) {
      val target = state.params.asInstanceOf[Vector2]
      val path = scene.findPath(pos, target)
      this.state = new MoveState(2, ListBuffer(path))
    } else {
      this.state = event.state
    }
  }

  def caskSkill(event: SkillEvent): Unit = {

  }

  def updateView(): Unit = {
    // views是视野里有玩家自己的玩家集合
    val newViews = scene.actors.values.filter(other => other != this && inRadius(other.pos, pos, other.viewSize)).toSet
    val enters = (newViews -- this.views).map(_.playerId)
    val leaves = (this.views -- newViews).map(_.playerId)
    this.views = newViews
    // 通知客户端
    PlayerChannel.tell(enters, 0x0210, MapBean("pid" -> playerId))
    PlayerChannel.tell(leaves, 0x0211, MapBean("pid" -> playerId))
  }

  def updatePos(deltaTime: Double): Unit = {
    state.`state` match {
      case 0 =>
      // 静止
      case 1 =>
        //  朝着指定方向移动
        val newPos = pos + towards * speed * deltaTime
        // 客户端和服务器分别计算
        if (scene.isWalkable(newPos)) pos = newPos
      case 2 =>
        // 自动寻路
        val proceed = speed * deltaTime
        val path = state.params.asInstanceOf[ListBuffer[Vector2]] // 路径点
      val target = path.head
        if (proceed >= (target - pos).len()) {
          // 到达节点
          path.remove(0)
          pos = target
          if (path.nonEmpty) {
            // 新的朝向
            towards = (path.head - pos).normalized
          } else {
            // 到达目标点
            state = MoveState(0)
          }
        } else pos += towards * proceed
    }
  }

  private def inRadius(v1: Vector2, v2: Vector2, radius: Float): Boolean = {
    Math.abs(v1.x - v2.x) <= radius && Math.abs(v1.z - v2.z) <= radius
  }
}