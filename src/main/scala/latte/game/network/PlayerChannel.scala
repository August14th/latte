package latte.game.network

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

/**
 * Created by linyuhe on 2019/4/24.
 */
object PlayerChannel {

  val connections = new ConcurrentHashMap[String, Connection]().asScala

  def login(playerId: String, connection: Connection) = connections += playerId -> connection

  def logout(playerId: String) = connections -= playerId

  def tell(playerId: String, eventId: Int, message: MapBean): Unit = {
    val conn = connections.get(playerId)
    if (conn.isDefined) conn.get.tell(eventId, message)
  }

  def tell(playerIds: Iterable[String], eventId: Int, message: MapBean): Unit = {
    playerIds.foreach(pid => tell(pid, eventId, message))
  }
}
