package latte.game.config

/**
 * Created by linyuhe on 2018/5/19.
 */

object SceneConfig {

  val configs = Map(10001 -> SceneConfig(10001, 1))

  def apply(id: Int): SceneConfig = configs(id)

}

// type: 0主城,1野外地图,2单人副本3多人副本
case class SceneConfig(id: Int, `type`: Int)

