package latte.game.scene

import java.util.concurrent.{Executors, TimeUnit}

import latte.game.config.SceneConfig
import latte.game.server.{GameException, Manager, Player}

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

class Scene(val sceneId: Int, val sceneConfig: SceneConfig) extends Grids(sceneConfig.id + ".bytes") {

  private val frame = 10
  // 玩家的视野
  var movements = new Movement(this)
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
    movements.tick(deltaTime)
  }

  protected def tryEnter(player: Player) = {

  }

  // 进入场景
  def enter(player: Player, pos: Vector2, angle: Int) = {
    player.scene.foreach(_.tryLeave(player))
    tryEnter(player)
    if (!isWalkable(pos)) throw new GameException("Not reachable.")
    player.scene.foreach(_.leave(player))
    // 设置新的坐标位置
    movements.addPlayer(player, pos, angle)
    player.scene = Some(this)
    onPlayerEnter(player)
  }

  protected def onPlayerEnter(player: Player): Unit = {

  }

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
    player.scene = None
    movements.removePlayer(player)
    onPlayerLeave(player)
  }

  protected def onPlayerLeave(player: Player): Unit = {

  }
}