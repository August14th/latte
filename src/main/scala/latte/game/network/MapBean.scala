package latte.game.network

import java.util
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

  def toJava(value: Any): Any = value match {
    case a: Array[_]@unchecked =>
      val list = new util.ArrayList[Any]()
      for (e <- a) {
        list.add(toJava(e))
      }
      list
    case l: Seq[_]@unchecked =>
      val list = new util.ArrayList[Any]()
      for (e <- l) {
        list.add(toJava(e))
      }
      list
    case s: scala.collection.Set[_]@unchecked =>
      val set = new util.HashSet[Any]()
      for (e <- s) {
        set.add(toJava(e))
      }
      set
    case m: scala.collection.Map[String, _]@unchecked =>
      val map = new util.HashMap[String, Any]()
      for ((k, v) <- m) {
        map.put(k, toJava(v))
      }
      map
    case _ =>
      value
  }
}


class MapBean extends mutable.HashMap[String, Any] {

  def get[T](key: String, defaultValue: T): T = {
    val v = get(key)
    if (v.isDefined) v.get.asInstanceOf[T] else defaultValue
  }

  def ++(that: MapBean) = MapBean(this.toSeq ++ that.toSeq: _*)

  def getString(key: String): String = this.get(key, null)

  def getInt(key: String): Int = this.get(key, 0)

  def getDate(key: String): Date = this.get(key, null)

  def toJavaMap = MapBean.toJava(this)

}
