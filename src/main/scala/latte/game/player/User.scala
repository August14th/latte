package latte.game.player

import latte.game.PlayerNotFoundException
import latte.game.network.MapBean

/**
 * Created by linyuhe on 2018/9/13.
 */

class User(val id: String) {

  loadFromDB()

  private def loadFromDB() = {
    if(id == "10001") this.name = "latte"
    else throw new PlayerNotFoundException(id)
  }

  var name: String = _

  def persist() = {

  }

  def toMapBean = MapBean("id" -> id, "name" -> name)

}
