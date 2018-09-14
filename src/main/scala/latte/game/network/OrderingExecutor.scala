package latte.game.network

import java.util
import java.util.concurrent.Executors

import scala.collection.mutable

/**
 * Created by linyuhe on 2018/9/12.
 */
object OrderingExecutor {

  val executor = Executors.newCachedThreadPool()

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


