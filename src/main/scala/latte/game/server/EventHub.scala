package latte.game.server

import latte.game.network.OrderingExecutor

import scala.collection.mutable.ListBuffer
import scala.reflect._

/**
 * Created by linyuhe on 2018/9/27.
 */

abstract class IEvent(val eventId: Int) {

}

object EventHub {

  private val listeners = collection.mutable.Map.empty[ClassTag[_], ListBuffer[_ => Unit]]

  def publish[T <: IEvent : ClassTag](event: T): Unit = {
    val list = listeners.synchronized(listeners.get(classTag[T]).map(_.toList))
    if (list.nonEmpty) {
      // 每个监听器都串行处理事件，不同监听器是并发的
      list.get.foreach { case f: (T => Unit) => OrderingExecutor.orderingExecute[IEvent](f, f(event)) }
    }
  }

  def subscribe[T <: IEvent : ClassTag](listener: T => Unit): Unit = {
    listeners.synchronized {
      val list = listeners.getOrElseUpdate(classTag[T], ListBuffer.empty)
      list += listener
    }
  }

  def unSubscribe[T <: IEvent : ClassTag](listener: T => Unit): Unit = {
    listeners.synchronized {
      val list = listeners.get(classTag[T])
      if (list.nonEmpty) {
        list.get -= listener
        if (list.get.isEmpty) listeners -= classTag[T]
      }
    }
  }

}

object Test extends App {
  case class LevelUpgrade(playerId: String, level: Int) extends IEvent(10) {}

  EventHub.subscribe((h: LevelUpgrade) => println(h.playerId))
  EventHub.publish(LevelUpgrade("10001", 10))

}



