package latte.game.scene

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import latte.game.config.MapConfig
import latte.game.network.MapBean
import latte.game.server.{GameException, Player}

import scala.collection.JavaConverters._

/**
 * Created by linyuhe on 2018/5/19.
 */

class Event(val t: Int)

case class MoveEvent(pos: Vector2, towards: Vector2, state: MoveState) extends Event(1)

case class SkillEvent(pos: Vector2, skillId: Int, target: String) extends Event(2)

case class MoveState(state: Int, params: Object = null)

abstract class SceneGroup(t: Int) {

  val maps = MapConfig.configs.values.filter(_.`t` == t).toList

  def assignScene(player: Player, mapId: Int): Scene
}

object Scene {
  // id产生器
  private val idGen = new AtomicInteger()
  // 所有场景共用的线程池
  private val scheduler = Executors.newScheduledThreadPool(16)
  // 玩家和场景的对应关系
  private val players = new ConcurrentHashMap[String, Scene].asScala
  // 按场景类型分组
  val sceneGroups = Map(1 -> Country)

  def getSceneOf(playerId: String) = players.get(playerId)

  def enterScene(player: Player, mapId: Int, pos: Vector2, towards: Vector2): Unit = {
    val conf = MapConfig.configs(mapId)
    val scene = sceneGroups(conf.`t`).assignScene(player, mapId)
    // 进入副本
    scene.enter(player, pos, towards)
    players += player.id -> scene
  }

  def leaveScene(playerId: String): Unit = {
    val sceneOpt = players.remove(playerId)
    if (sceneOpt.isDefined) sceneOpt.get.leave(playerId)
  }
}

class Scene(val mapConfig: MapConfig, val id: Int = Scene.idGen.incrementAndGet()) extends Grid(mapConfig.id + ".bytes") {
  // 每秒刷新频率
  private val frame = 20
  // 场景内的所有玩家
  var actors = new ConcurrentHashMap[String, Actor].asScala
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
  // 玩家的输入，移动和释放技能
  private val events = new LinkedBlockingQueue[(String, Event)]

  def addEvent(playerId: String, event: Event) = events.synchronized(events.put(playerId, event))

  // 主循环
  private def tick(deltaTime: Double) {
    // 及时处理事件
    val (moveEvents, skillEvents) = events.synchronized({
      val partitions = events.asScala.partition(e => e._2.t == 1)
      events.clear()
      partitions
    })
    // 移动
    moveEvents.foreach {
      case (playerId, event) =>
        val actorOpt = actors.get(playerId)
        if (actorOpt.isDefined)
          actorOpt.get.move(event.asInstanceOf[MoveEvent])
    }
    actors.values.foreach(_.updatePos(deltaTime))
    // 释放技能
    skillEvents.foreach {
      case (playerId, event) =>
        val actorOpt = actors.get(playerId)
        if (actorOpt.isDefined)
          actorOpt.get.caskSkill(event.asInstanceOf[SkillEvent])
    }
    // 更新视野
    actors.values.foreach(_.updateView())
  }

  protected def tryEnter(player: Player) {}

  // 进入场景
  def enter(player: Player, pos: Vector2, towards: Vector2) = {
    if (!isWalkable(pos)) throw new GameException("Not reachable.")
    tryEnter(player)
    Scene.leaveScene(player.id)
    // 设置新的坐标位置
    actors += player.id -> new Actor(player.id, this, pos, towards)
    onPlayerEnter(player)
  }

  protected def onPlayerEnter(player: Player) {}

  // 结算
  def close(): Unit = {
    timer.cancel(true)
    onClose()
  }

  protected def onClose() {}

  private def leave(playerId: String): Unit = {
    actors -= playerId
    onPlayerLeave(playerId)
  }

  protected def onPlayerLeave(playerId: String): Unit = {

  }

  def tell(playerId: String, cmd: Int, event: MapBean) = {
    Player.applyIfOnline(playerId) {
      _.tell(cmd, event)
    }
  }
}