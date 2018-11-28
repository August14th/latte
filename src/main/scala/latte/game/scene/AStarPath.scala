package latte.game.scene

/**
 * Created by linyuhe on 2018/11/27.
 */
class AStarPath(val mapGrids: MapGrids, val start: MapGrid, val end: MapGrid) {

  var list = List(Node(null, start, 0, Math.abs(start.row - end.row) + Math.abs(start.column - end.column)))

  def find(): List[MapGrid] = compress(mergePath(find0()))

  private def compress(path: List[MapGrid]): List[MapGrid] = {
    var list = List.empty[MapGrid]
    list ::= path.head // 起点
    while(list.head != path.last){
      list ::= path.reverse.find(isNotStop(list.head, _)).get
    }
    list.reverse
  }

  private def isNotStop(g1: MapGrid, g2: MapGrid): Boolean = {
    if(g1 == g2) return true
    val start = g1.center
    val end = g2.center
    val direction = (end - start).normalized

    var node = start // 从start到end途中经过的grid
    while (mapGrids.getGrid(node) != g2) {
      node += direction * mapGrids.size
      if (!mapGrids.isWalkable(node))
        return false
    }
    true
  }

  private def mergePath(path: List[MapGrid]): List[MapGrid] = {
    var nodes = path
    var list = List.empty[MapGrid]
    var delta = (0, 0)

    while (nodes.length > 1) {
      val temp = (nodes(1).column - nodes.head.column, nodes(1).row - nodes.head.row)
      if (list.isEmpty || delta != temp) {
        list ::= nodes.head
        delta = temp
      }
      nodes = nodes.drop(1)
    }
    (nodes.head :: list).reverse
  }

  private def find0(): List[MapGrid] = {
    var minNode = getMinNode
    while (minNode != null) {
      minNode.checked = true
      val surroundings = surroundNodes(minNode)
      surroundings.foreach(child => {
        if (child.grid == end) {
          var path = List.empty[MapGrid]
          var node = child
          while (node != null) {
            path ::= node.grid
            node = node.parent
          }
          return path
        } else {
          val n = list.find(node => node.grid == child.grid)
          if (n.isEmpty) list ::= child
          else {
            val node = n.get
            if (!node.checked && node.G > child.G) {
              node.parent = child.parent
              node.G = child.G
            }
          }
        }
      })
      minNode = getMinNode
    }
    throw new Exception("Path not found.")
  }

  def getMinNode = list.filter(!_.checked).minBy(_.F)

  def surroundNodes(node: Node) = {
    var surroundings = List.empty[Node]
    for (r <- -1 to 1) {
      for (c <- -1 to 1) {
        if (r != 0 || c != 0) {
          val row = node.grid.row + r
          val column = node.grid.column + c
          if (row >= 0 && row < mapGrids.rows && column >= 0 && column < mapGrids.columns) {
            val nextGrid = mapGrids.getGrid(row, column)
            if (nextGrid.isWalkable) {
              val continue = if (r == 0 || c == 0) true
              // 有拐角的情况
              else mapGrids.getGrid(row, node.grid.column).isWalkable && mapGrids.getGrid(node.grid.row, column).isWalkable
              if (continue) {
                val g = node.G + (if (r == 0 || c == 0) 10 else 14)
                val h = Math.abs(nextGrid.row - end.row) + Math.abs(nextGrid.column - end.column)
                surroundings ::= Node(node, nextGrid, g, h)
              }
            }
          }
        }
      }
    }
    surroundings
  }
}


case class Node(var parent: Node, grid: MapGrid, var G: Int, var H: Int, var checked: Boolean = false) {

  def F = G + H

}
