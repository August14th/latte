package latte.game.command

import latte.game.network.MapBean
import latte.game.scene.{MoveState, Scene, Vector2}
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
    val scene = Scene(sceneId)
    scene.get.enter(player, Vector2(x, z), angle)
    MapBean("sceneId" -> sceneId, "x" -> (x * 100).toInt, "z" -> (z * 100).toInt, "angle" -> angle)
  }

  /**
   * 移动
   */
  def handler02(player: Player, request: MapBean): MapBean = {
    val x = request.getInt("x") / 100f
    val z = request.getInt("z") / 100f
    val angle = request.getInt("angle") / 100f
    val state = request.getInt("state")
    player.movement match {
      case Some(movement) =>
        val newPos = Vector2(x, z)
        val newForward = Vector2.fromAngle(angle)
        println(s"pos:${movement.position}, newPos:$newPos, forward:$newForward")
        movement.setPosition(newPos, newForward, MoveState(state))
      case None =>
    }
    MapBean.empty
  }
}
