package latte.game.scene

import latte.game.network.MapBean
import latte.game.server._

class Movement(val scene: Scene) {

  private val ViewSize = 3f

  private var movements = Map.empty[Player, PlayerMovement]

  def addPlayer(player: Player, pos: Vector2, angle: Int): Unit = {
    val movement = new PlayerMovement(player)
    movements += player -> movement
    // 新的位置
    movement.setPosition(pos, angle, MoveState(0))
  }

  def removePlayer(player: Player) = movements -= player

  def movement(player: Player): Option[PlayerMovement] = movements.get(player)

  def tick(deltaTime: Double) = movements.values.foreach(_.tick(deltaTime))

  private def surrounding(player: Player, radius: Float) = {
    movements.values.filter(other => other.player != player && inRadius(movements(player).position, other.position, radius)).toSet
  }

  private def inRadius(v1: Vector2, v2: Vector2, radius: Float): Boolean = {
    val v1Grid = scene.getGrid(v1)
    val v2Grid = scene.getGrid(v2)
    if (v1Grid.isDefined && v2Grid.isDefined) {
      val size = radius / scene.size
      Math.abs(v1Grid.get.row - v2Grid.get.row) <= size &&
        Math.abs(v1Grid.get.column - v2Grid.get.column) <= size
    } else false
  }

  // 场景负责玩家的位置更新
  class PlayerMovement(val player: Player) {
    // 场景内的位置
    private var pos: Vector2 = Vector2.zero
    // 朝向
    private var angle: Int = 0
    // 状态
    private var state: MoveState = MoveState(0)

    def tick(deltaTime: Double): Unit = {
      state.`type` match {
        case 0 =>
        case 1 =>
          //  朝着指定方向移动
          val forward = Vector2(math.sin(math.toRadians(angle)), math.cos(math.toRadians(angle)))
          val newPos = pos + forward * player.speed * deltaTime
          // 客户端和服务器分别计算
          if (scene.isWalkable(newPos)) setPos(newPos)
        case 2 =>
          // 自动寻路
          val forward = Vector2(math.sin(math.toRadians(angle)), math.cos(math.toRadians(angle)))
          val newPos = pos + forward * player.speed * deltaTime
          val path = state.params.asInstanceOf[List[Vector2]] // 路径点
        val target = scene.getGrid(path.head)
          if (scene.getGrid(newPos) == target) {
            // 到达结点
            if (path.tail.nonEmpty) {
              // 新的结点
              val angle = (path(1) - newPos).angle().toInt
              setPosition(newPos, angle, MoveState(2, path.tail))
            } else {
              // 到达目标点
              setPosition(newPos, angle, MoveState(0))
            }
          } else setPos(newPos)
      }
    }

    def position = pos

    def setPosition(newPos: Vector2, newAngle: Int, newState: MoveState): Unit = {
      // 设置新的坐标
      if (scene.isWalkable(newPos)) {
        pos = newPos
        angle = newAngle
        state = newState
        player.tell(0x0202, MapBean("sceneId" -> scene.sceneId, "x" -> (pos.x * 100).toInt, "z" -> (pos.z * 100).toInt,
          "angle" -> angle, "state" -> state.`type`, "speed" -> player.speed))
        // 更新视野
        updateView()
      }
    }

    def moveTowards(angle: Int): Unit = {
      setPosition(pos, angle, MoveState(1))
    }

    def stopMoving(): Unit = {
      this.setPosition(pos, angle, MoveState(0))
    }

    def moveToTarget(x: Double, z: Double): Unit = {
      val target = Vector2(x, z)
      val inSameGrid = scene.getGrid(pos) == scene.getGrid(target)
      if (!inSameGrid) {
        val path = scene.findPath(pos, target)
        val angle = (path(1) - pos).angle().toInt
        this.setPosition(pos, angle, MoveState(2, path.tail))
      } else {
        val angle = (target - pos).angle().toInt
        this.setPosition(pos, angle, MoveState(0))
      }
    }

    private def setPos(newPos: Vector2) = {
      if (scene.isWalkable(pos)) {
        pos = newPos
        // 更新视野
        updateView()
      }
    }

    //  视野内的所有玩家
    private var views = Set.empty[PlayerMovement]

    // 根据在场景内的位置更新视野
    private def updateView(): Unit = {
      val neighbors = surrounding(player, ViewSize)
      val enters = neighbors -- views
      val leaves = views -- neighbors
      leaveView(leaves)
      enterView(enters)
    }

    // 有玩家进入
    private def enterView(others: Set[PlayerMovement]): Unit = {
      val enters = others -- views
      if (enters.nonEmpty) {
        views = views ++ enters
        player.tell(0x0210, MapBean("enters" -> enters.map(_.player.id)))
        // 自己进入其他玩家的视野
        enters.foreach(other => other.enterView(Set(this)))
      }
    }

    // 有玩家离开
    private def leaveView(others: Set[PlayerMovement]): Unit = {
      val leaves = others & views
      if (leaves.nonEmpty) {
        views = views -- leaves
        player.tell(0x0211, MapBean("leaves" -> leaves.map(_.player.id)))
        // 自己离开其他玩家的视野
        leaves.foreach(other => other.leaveView(Set(this)))
      }
    }
  }

}

case class MoveState(`type`: Int, params: Object = null) {

}




