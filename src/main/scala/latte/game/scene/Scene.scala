package latte.game.scene

import java.util.concurrent.{TimeUnit, Executors}

import latte.game.config.SceneConfig
import latte.game.network.MapBean
import latte.game.server.{GameException, Component, Manager, Player}

/**
 * Created by linyuhe on 2018/5/19.
 */
object Scene extends Manager {

  val scheduler = Executors.newScheduledThreadPool(16)

  // 所有场景
  var scenes = Map.empty[Int, Scene]

  def apply(sceneId: Int) = scenes(sceneId)

  override def start(): Unit = {
    scenes = SceneConfig.configs.values.filter(conf => conf.`type` == 0 || conf.`type` == 1)
      .map(conf => conf.id -> new Scene(conf.id, conf)).toMap
  }

}

class Scene(val sceneId: Int, val sceneConfig: SceneConfig) {

  private val frame = 10
  // 网格
  val grids = MapGrids(sceneId + ".bytes")
  // 玩家的视野
  private var players = Set.empty[Player]
  // 线程
  val timer = Scene.scheduler.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = {
      try {
        tick(1d / frame)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }, 1000 / frame, 1000 / frame, TimeUnit.MILLISECONDS)

  // 主循环
  def tick(deltaTime: Double) {
    // movement
    players.foreach(player => {
      player.state.tick(deltaTime)
    })
  }

  protected def tryEnter(player: Player) = {
    true
  }

  // 进入场景
  def enter(player: Player, coord: Vector2, angle: Int) = {
    // 退出上一个场景
    val pos = player.state.pos
    if (pos != null) pos.scene.tryLeave(player)
    tryEnter(player)
    if (!grids.isWalkable(coord)) throw new GameException("Not reachable.")
    if (pos != null) pos.scene.leave(player)
    // 设置新的坐标位置
    player.state.setPosition(this, coord, angle, Move(0))
    players += player
    onPlayerEnter(player)
  }

  protected def onPlayerEnter(player: Player): Unit = {

  }

  def isWalkable(pos: Vector2) = {
    val grid = grids.getGrid(pos)
    grid != null && grid.isWalkable
  }

  def findPath(start: Vector2, end: Vector2) = {
    val startGrid = grids.getGrid(start)
    val endGrid = grids.getGrid(end)

    if (startGrid != null && startGrid.isWalkable && endGrid != null && endGrid.isWalkable) {
      grids.findPath(startGrid, endGrid)
    } else {
      throw new GameException("target is not reachable.")
    }
  }

  def surroundingPlayers(player: Player, radius: Float) = {
    val pos = player.state.pos.grid
    players.filter(other => {
      val otherPos = other.state.pos.grid
      Math.abs(otherPos.row - pos.row) <= radius / grids.size || Math.abs(otherPos.column - pos.column) <= radius / grids.size
    })
  }

  def tellAllPlayers(cmd: Int, event: MapBean) = players.foreach(_.tell(cmd, event))

  // 释放技能
  def skill(player: Player) = {

  }

  // 结算
  def close(): Unit = {
    timer.cancel(true)
  }


  protected def tryLeave(player: Player) = {

  }

  private def leave(player: Player): Unit = {
    players -= player
    onPlayerLeave(player)
  }

  protected def onPlayerLeave(player: Player): Unit = {

  }
}