package latte.game.server

import java.util
import java.util.concurrent.Executors

import latte.game.network.MapBean

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect._

/**
 * 发布消息，每个系统都有自己的消息id
 *
 * Created by linyuhe on 2018/9/27.
 */

object EventHub {

  type Listener = MapBean => Any

  val executors = new OrderingExecutor(8)

  def apply(owner: Any) = new EventHub(owner)

}

class EventHub(val owner: Any) {

  private val listeners = collection.mutable.Map.empty[(ClassTag[_], Int), ListBuffer[EventHub.Listener]]

  def publish[T: ClassTag](eventId: Int, params: MapBean): Unit = {
    val list = listeners.synchronized(listeners.get(classTag[T], eventId).map(_.toList))
    if (list.nonEmpty) {
      // 每个监听器都串行处理事件，不同监听器是并发的
      list.get.foreach { case h => EventHub.executors.orderingExecute((owner, h), h(params)) }
    }
  }

  def subscribe[T: ClassTag](eventId: Int, listener: EventHub.Listener): Unit = {
    listeners.synchronized {
      val list = listeners.getOrElseUpdate((classTag[T], eventId), ListBuffer.empty)
      list += listener
    }
  }

  def unSubscribe[T: ClassTag](eventId: Int, listener: EventHub.Listener): Unit = {
    listeners.synchronized {
      val list = listeners.get(classTag[T], eventId)
      if (list.nonEmpty) {
        list.get -= listener
        if (list.get.isEmpty) listeners -= ((classTag[T], eventId))
      }
    }
  }
}

class OrderingExecutor(val poolSize: Int) {

  private val executor = Executors.newFixedThreadPool(poolSize)

  val tasks = mutable.Map.empty[Any, util.Queue[OrderingTask]]

  def orderingExecute(key: Any, task: Runnable) {
    var head = false
    val orderingTask = new OrderingTask(key, task)
    tasks.synchronized {
      val queue = tasks.getOrElseUpdate(key, {
        head = true
        new util.LinkedList()
      })
      if (!head) queue.offer(orderingTask)
    }
    if (head) executor.execute(orderingTask)
  }

  def orderingExecute(key: Any, task: => Any) {
    orderingExecute(key, new Runnable {
      override def run() = task
    })
  }

  class OrderingTask(key: Any, task: Runnable) extends Runnable {

    override def run() = {
      try {
        task.run()
      } finally {
        tasks.synchronized {
          val nextTask = tasks(key).poll()
          if (nextTask != null) executor.execute(nextTask)
          else tasks -= key
        }
      }
    }
  }
}



