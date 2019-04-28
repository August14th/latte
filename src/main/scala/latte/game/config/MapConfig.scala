package latte.game.config

/**
 * Created by linyuhe on 2018/5/19.
 */

object MapConfig {

  val configs = Map(1001 -> MapConfig(1001, 1))

  def apply(id: Int): MapConfig = configs(id)

}

// type: 0主城,1野外地图,2单人副本3多人副本
case class MapConfig(id: Int, t: Int)

