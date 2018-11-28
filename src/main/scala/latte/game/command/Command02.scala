package latte.game.command

import latte.game.network.{Request, MapBean}
import latte.game.scene.{Vector2, Scene}
import latte.game.server.{Player, Command}

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
    val angle = request.getInt("angle")
    val scene = Scene(sceneId)
    scene.enter(player, Vector2(x, z), angle)
    MapBean()
  }

  /**
   * 移动
   */
  def handler02(player: Player, request: MapBean): MapBean = {
    val angle = request.getInt("angle")
    player.state.moveTowards(angle)
    MapBean.empty
  }

  /**
   * 停止移动
   */
  def handler03(player: Player, request: MapBean): MapBean = {
    player.state.stopMoving()
    MapBean.empty
  }

  /**
   * 自动寻路到目标点
   */
  def handler04(player: Player, request: MapBean): MapBean = {
    val x = request.getInt("x") / 100d
    val z = request.getInt("z") / 100d

    player.state.moveToTarget(x, z)
    MapBean.empty
  }
}
