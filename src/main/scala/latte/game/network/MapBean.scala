package latte.game.network

import java.util.Date

import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.mutable

/**
 * Created by linyuhe on 2018/5/19.
 */

object MapBean {

  def empty = new MapBean

  def apply(elems: (String, Any)*) = empty ++= elems

  def apply(map: java.util.Map[String, Any]) = empty ++= mapAsScalaMap(map).toList
}

class MapBean extends mutable.HashMap[String, Any] {

  def get[T](key: String, defaultValue: T): T = {
    val v = get(key)
    if (v.isDefined) v.get.asInstanceOf[T] else defaultValue
  }

  def getString(key: String): String = this.get(key, "")

  def getInt(key: String): Int = this.get(key, 0)

  def getDate(key: String): Date = this.get(key, null)

}
