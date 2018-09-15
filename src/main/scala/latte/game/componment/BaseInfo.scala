package latte.game.componment

import latte.game.network.{GameException, MapBean}

/**
 * Created by linyuhe on 2018/9/13.
 */

object BaseInfo {

  def apply(id: String): Unit = {
    val user = loadFromDB(id)
    if (user == null) throw PlayerNotFoundException(id)
  }

  def loadFromDB(id: String): BaseInfo = {
    val bi = new BaseInfo(id)
    bi.name = "latte"
    bi
  }
}

class BaseInfo(id: String) {

  var name: String = this.id

  def persist() = {

  }

  def toMapBean = MapBean("id" -> id, "name" -> name)

}


case class PlayerNotFoundException(playerId: String) extends GameException(s"Player:$playerId not found")
