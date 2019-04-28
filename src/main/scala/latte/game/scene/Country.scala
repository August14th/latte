package latte.game.scene

import latte.game.server.Player

/**
 * Created by linyuhe on 2019/4/10.
 */
object Country extends SceneGroup(1) {

  private val scenes = maps.map(map => map.id -> new Scene(map)).toMap

  def assignScene(player: Player, mapId: Int): Scene = scenes(mapId)

}