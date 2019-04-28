package latte.game.command

import latte.game.network.MapBean
import latte.game.scene.{MoveEvent, MoveState, Scene, Vector2}
import latte.game.server.{Command, Player}

/**
 * Created by linyuhe on 2018/5/19.
 */
object Command02 extends Command {

  /**
   * 进入场景
   */
  def handler01(player: Player, request: MapBean): MapBean = {
    val sceneId = request.getInt("sceneId")
    val x = request.getInt("x") / 100f
    val z = request.getInt("z") / 100f
    val angle = request.getInt("angle") / 100f
    Scene.enterScene(player, sceneId, Vector2(x, z), Vector2.fromAngle(angle))
    MapBean("sceneId" -> sceneId, "x" -> (x * 100).toInt, "z" -> (z * 100).toInt, "angle" -> angle)
  }

  /**
   * 移动
   */
  def handler02(player: Player, request: MapBean): MapBean = {
    val x = request.getInt("x") / 100f
    val z = request.getInt("z") / 100f
    val angle = request.getInt("angle") / 100f // 朝向
    val state = request.getInt("state") // 状态，0 停止，1 向前移动

    val sceneOpt = Scene.getSceneOf(player.id)
    if (sceneOpt.isDefined) {
      sceneOpt.get.addEvent(player.id, MoveEvent(Vector2(x, z),
        Vector2.fromAngle(angle), MoveState(state)))
    }
    MapBean.empty
  }
}
