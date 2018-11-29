package latte.game.scene

import java.io.FileInputStream

import latte.game.server.GameException

/**
 * Created by linyuhe on 2018/11/23.
 */

class Grids(file: String) {

  val (start, size, rows, columns, grids) = fromBytes(file)

  def fromBytes(file: String) = {
    val reader = new FileInputStream(file)
    try {
      val bytes = new Array[Byte](4)
      reader.read(bytes)
      val _x = toInt(bytes) / 100f // 起点坐标x
      reader.read(bytes)
      val _z = toInt(bytes) / 100f // 起点坐标z
      reader.read(bytes)
      val size = toInt(bytes) / 100f // grid的大小
      reader.read(bytes)
      val rows = toInt(bytes) // 行
      reader.read(bytes)
      val columns = toInt(bytes) // 列

      val grids = Array.ofDim[Grid](rows, columns)
      for {
        row <- 0 until rows
        column <- 0 until columns} {
        val `type` = reader.read
        grids(row)(column) = new Grid(row, column, `type`)
      }
      (Vector2(_x, _z), size, rows, columns, grids)
    } finally {
      reader.close()
    }
  }

  private def toInt(bytes: Array[Byte]) = {
    (bytes(0) & 0xff) | (bytes(1) & 0xff) << 8 | (bytes(2) & 0xff) << 16 | (bytes(3) & 0xff) << 24
  }

  def getGrid(pos: Vector2): Option[Grid] = {
    val row = ((pos.z - start.z) / size).toInt
    val column = ((pos.x - start.x) / size).toInt
    getGrid(row, column)
  }

  def getGrid(row: Int, column: Int): Option[Grid] = {
    if (row >= 0 && row < rows && column >= 0 && column < columns) {
      Some(grids(row)(column))
    } else None
  }

  // 是否可达
  def isWalkable(position: Vector2) = {
    val grid = getGrid(position)
    if (grid.isDefined) grid.get.isWalkable
    else false
  }

  def isWalkable(row: Int, column: Int) = {
    val grid = getGrid(row, column)
    if (grid.isDefined) grid.get.isWalkable
    else false
  }

  // A*寻路
  def findPath(from: Vector2, to: Vector2) = {
    if (isWalkable(from) && isWalkable(to)) {
      new AStarPath(getGrid(from).get, getGrid(to).get).find().map(_.center)
    }
    else throw new GameException("target is not reachable.")
  }

  class Grid(val row: Int, val column: Int, val `type`: Int) {

    private var areaId: Int = 0

    def setAreaId(areaId: Int) = this.areaId = areaId

    def getAreaId = areaId

    def isWalkable = `type` == 1 || `type` == 2

    def center = {
      val halfSize = size / 2
      val x = start.x + column * size + halfSize
      val z = start.z + row * size + halfSize

      Vector2(x, z)
    }

    override def toString = s"($row, $column)"
  }

  class AStarPath(val from: Grid, val to: Grid) {

    var list = List(Node(null, from, 0, Math.abs(from.row - to.row) + Math.abs(from.column - to.column)))

    def find(): List[Grid] = compress(mergePath(find0()))

    private def compress(path: List[Grid]): List[Grid] = {
      var list = List.empty[Grid]
      list ::= path.head // 起点
      while (list.head != path.last) {
        list ::= path.reverse.find(isNotStop(list.head, _)).get
      }
      list.reverse
    }

    private def isNotStop(g1: Grid, g2: Grid): Boolean = {
      if (g1 == g2) return true
      val from = g1.center
      val to = g2.center
      val direction = (to - from).normalized
      var position = from // 从start到end途中经过的grid
      var grid = getGrid(position).get
      var prev: Grid = null
      while (grid != g2) {
        if (!grid.isWalkable) return false
        if (prev != null && prev.row != grid.row && prev.column != grid.column) {
          // 斜方向
          if (!isWalkable(prev.row, grid.column) || !isWalkable(grid.row, prev.column))
            return false
        }
        prev = grid
        position += direction * size
        grid = getGrid(position).get
      }
      true
    }

    private def mergePath(path: List[Grid]): List[Grid] = {
      var grids = path
      var list = List.empty[Grid]
      var delta = (0, 0)

      while (grids.length > 1) {
        val temp = (grids(1).row - grids.head.row, grids(1).column - grids.head.column)
        if (list.isEmpty || delta != temp) {
          list ::= grids.head
          delta = temp
        }
        grids = grids.drop(1)
      }
      (grids.head :: list).reverse
    }

    private def find0(): List[Grid] = {
      var minNode = getMinNode
      while (minNode != null) {
        minNode.checked = true
        val surroundings = surroundNodes(minNode)
        surroundings.foreach(child => {
          if (child.grid == to) {
            var path = List.empty[Grid]
            var node = child
            while (node != null) {
              path ::= node.grid
              node = node.parent
            }
            return path
          } else {
            val opt = list.find(node => node.grid == child.grid)
            if (opt.isEmpty) list ::= child
            else {
              val node = opt.get
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
      for {r <- -1 to 1
           c <- -1 to 1} {
        if (r != 0 || c != 0) {
          val row = node.grid.row + r
          val column = node.grid.column + c
          val opt = getGrid(row, column)
          if (opt.isDefined) {
            val nextGrid = opt.get
            if (nextGrid.isWalkable) {
              val continue = if (r == 0 || c == 0) true
              // 有拐角的情况
              else isWalkable(row, node.grid.column) && isWalkable(node.grid.row, column)
              if (continue) {
                val g = node.G + (if (r == 0 || c == 0) 10 else 14)
                val h = Math.abs(nextGrid.row - to.row) + Math.abs(nextGrid.column - to.column)
                surroundings ::= Node(node, nextGrid, g, h)
              }
            }
          }
        }
      }
      surroundings
    }
  }

  case class Node(var parent: Node, grid: Grid, var G: Int, var H: Int, var checked: Boolean = false) {

    def F = G + H

  }

}