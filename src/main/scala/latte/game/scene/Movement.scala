package latte.game.scene

import latte.game.network.MapBean
import latte.game.server._

import scala.collection.mutable.ListBuffer

/**
 * Created by linyuhe on 2018/11/23.
 */
object Movement extends Manager {

  def apply(player: Player): Movement = {
    ProxyFactory.getSyncProxy(new Movement(player))
  }
}

case class Move(`type`: Int, params: Object = null) {

}

class Movement private(player: Player) extends Component(player) {

  var pos: Position = null

  var speed: Double = 5d

  override def tick(deltaTime: Double): Unit = {
    if (pos != null) {
      pos.move.`type` match {
        case 0 =>
        case 1 =>
          //  朝着指定方向移动
          val forward = Vector2(math.sin(math.toRadians(pos.angle)), math.cos(math.toRadians(pos.angle)))
          val newPos = this.pos.coord + forward * speed * deltaTime
          // 客户端和服务器分别计算
          if (pos.scene.isWalkable(newPos)) setCoord(newPos)
        case 2 =>
          // 自动寻路
          val forward = Vector2(math.sin(math.toRadians(pos.angle)), math.cos(math.toRadians(pos.angle)))
          val newPos = this.pos.coord + forward * speed * deltaTime
          val path = pos.move.params.asInstanceOf[List[MapGrid]]
          if (pos.scene.grids.getGrid(newPos) == path.head) {
            // 到达结点
            if (path.tail.nonEmpty) {
              // 新的结点
              val nextTarget = path(1).center
              val angle = (nextTarget - newPos).angle().toInt
              setPosition(pos.scene, newPos, angle, Move(2, path.tail))
            } else {
              // 到达目标点
              setPosition(pos.scene, newPos, pos.angle, Move(0))
            }
          } else setCoord(newPos)
      }
    }
  }

  def setCoord(coord: Vector2) = {
    pos.coord = coord
    // 更新视野
    player.view.updateView()
  }

  def setPosition(scene: Scene, coord: Vector2, angle: Int, move: Move): Unit = {
    // 设置新的坐标
    val newPos = new Position(scene, angle, move, coord)
    if (newPos.grid.isWalkable) {
      pos = newPos
      // 更新视野
      player.view.updateView()
      player.tell(0x0202, pos.toMapBean ++ MapBean("speed" -> speed, "playerId" -> player.id))
    }
  }

  def moveTowards(angle: Int): Unit = {
    this.setPosition(pos.scene, pos.coord, angle, Move(1))
  }

  def stopMoving(): Unit = {
    this.setPosition(pos.scene, pos.coord, pos.angle, Move(0))
  }

  def moveToTarget(x: Double, z: Double): Unit = {
    val target = Vector2(x, z)
    val inSameGrid = pos.grid == pos.scene.grids.getGrid(target)
    if (!inSameGrid) {
      val path = pos.scene.findPath(pos.coord, target)
      val angle = (path(1).center - pos.coord).angle().toInt
      this.setPosition(pos.scene, pos.coord, angle, Move(2, path.tail))
    } else {
      val angle = (target - pos.coord).angle().toInt
      this.setPosition(pos.scene, pos.coord, angle, Move(0))
    }
  }

  def getLastPosition = MapBean("sceneId" -> 10001, "x" -> 1730, "z" -> -3800, "angle" -> 0)

  override def start() = {}

  case class Position(scene: Scene, angle: Int, move: Move, var coord: Vector2) {
    // x,z坐标，angle角度，state状态：0静止1跑动

    def toMapBean = MapBean("sceneId" -> scene.sceneId, "x" -> (coord.x * 100).toInt, "z" -> (coord.z * 100).toInt,
      "angle" -> angle, "state" -> move.`type`)

    def grid = scene.grids.getGrid(coord)

  }

}


