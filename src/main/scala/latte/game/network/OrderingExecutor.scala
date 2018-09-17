package latte.game.network

import java.util
import java.util.concurrent.Executors

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect._

/**
 * Created by linyuhe on 2018/9/12.
 */
object OrderingExecutor {

  val executor = Executors.newCachedThreadPool()

  val tasks = mutable.Map.empty[Any, util.Queue[OrderingTask]]

  def orderingExecute[T: ClassTag](key: Any, task: Runnable) {
    var head = false
    val newKey = (classTag[T], key)
    val orderingTask = new OrderingTask(newKey, task)
    tasks.synchronized {
      val queue = tasks.getOrElseUpdate(newKey, {
        head = true
        new util.LinkedList()
      })
      if (!head) queue.offer(orderingTask)
    }
    if (head) executor.execute(orderingTask)
  }

  def orderingExecute[T: ClassTag](key: Any, task: => Any) {
    orderingExecute[T](key, new Runnable {
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


