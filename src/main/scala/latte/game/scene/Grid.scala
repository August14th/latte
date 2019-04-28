package latte.game.scene

import java.io.FileInputStream

import latte.game.server.GameException

/**
 * Created by linyuhe on 2018/11/23.
 */

class Grid(mapId: String) {

  private val (start, size, rows, columns, grid) = fromBytes(mapId)

  // 是否可达
  def isWalkable(position: Vector2) = {
    val cell = getCell(position)
    if (cell.isDefined) cell.get.isWalkable
    else false
  }

  // A*寻路
  def findPath(from: Vector2, to: Vector2) = {
    if (isWalkable(from) && isWalkable(to)) {
      new AStarPath(getCell(from).get, getCell(to).get).find().map(_.center)
    }
    else throw new GameException("target is not reachable.")
  }


  private def fromBytes(file: String) = {
    val reader = new FileInputStream(file)
    try {
      val bytes = new Array[Byte](4)
      reader.read(bytes)
      val _x = toInt(bytes) / 100f // 起点坐标x
      reader.read(bytes)
      val _z = toInt(bytes) / 100f // 起点坐标z
      reader.read(bytes)
      val size = toInt(bytes) / 100f // cell的大小
      reader.read(bytes)
      val rows = toInt(bytes) // 行
      reader.read(bytes)
      val columns = toInt(bytes) // 列

      val grid = Array.ofDim[Cell](rows, columns)
      for {
        row <- 0 until rows
        column <- 0 until columns} {
        val `type` = reader.read
        grid(row)(column) = new Cell(row, column, `type`)
      }
      (Vector2(_x, _z), size, rows, columns, grid)
    } finally {
      reader.close()
    }
  }

  private def toInt(bytes: Array[Byte]) = {
    (bytes(0) & 0xff) | (bytes(1) & 0xff) << 8 | (bytes(2) & 0xff) << 16 | (bytes(3) & 0xff) << 24
  }

  private def getCell(pos: Vector2): Option[Cell] = {
    val row = ((pos.z - start.z) / size).toInt
    val column = ((pos.x - start.x) / size).toInt
    getCell(row, column)
  }

  private def getCell(row: Int, column: Int): Option[Cell] = {
    if (row >= 0 && row < rows && column >= 0 && column < columns) {
      Some(grid(row)(column))
    } else None
  }

  private def isWalkable(row: Int, column: Int) = {
    val cell = getCell(row, column)
    if (cell.isDefined) cell.get.isWalkable
    else false
  }

  class Cell(val row: Int, val column: Int, val `type`: Int) {

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

  class AStarPath(val from: Cell, val to: Cell) {

    var list = List(Node(null, from, 0, Math.abs(from.row - to.row) + Math.abs(from.column - to.column)))

    def find(): List[Cell] = compress(mergePath(find0()))

    private def compress(path: List[Cell]): List[Cell] = {
      var list = List.empty[Cell]
      list ::= path.head // 起点
      while (list.head != path.last) {
        list ::= path.reverse.find(isNotStop(list.head, _)).get
      }
      list.reverse
    }

    private def isNotStop(c1: Cell, c2: Cell): Boolean = {
      if (c1 == c2) return true
      val from = c1.center
      val to = c2.center
      val direction = (to - from).normalized
      var position = from // 从start到end途中经过的cell
      var cell = c1
      var prev: Cell = null
      while (cell != c2) {
        if (!cell.isWalkable) return false
        if (prev != null && prev.row != cell.row && prev.column != cell.column) {
          // 斜方向
          if (!isWalkable(prev.row, cell.column) || !isWalkable(cell.row, prev.column))
            return false
        }
        prev = cell
        position += direction * size
        cell = getCell(position).get
      }
      true
    }

    private def mergePath(path: List[Cell]): List[Cell] = {
      var cells = path
      var list = List.empty[Cell]
      var delta = (0, 0)

      while (cells.length > 1) {
        val temp = (cells(1).row - cells.head.row, cells(1).column - cells.head.column)
        if (list.isEmpty || delta != temp) {
          list ::= cells.head
          delta = temp
        }
        cells = cells.drop(1)
      }
      (cells.head :: list).reverse
    }

    private def find0(): List[Cell] = {
      var minNode = getMinNode
      while (minNode != null) {
        minNode.checked = true
        val surroundings = surroundNodes(minNode)
        surroundings.foreach(child => {
          if (child.cell == to) {
            var path = List.empty[Cell]
            var node = child
            while (node != null) {
              path ::= node.cell
              node = node.parent
            }
            return path
          } else {
            val opt = list.find(node => node.cell == child.cell)
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
          val row = node.cell.row + r
          val column = node.cell.column + c
          val opt = getCell(row, column)
          if (opt.isDefined) {
            val nextCell = opt.get
            if (nextCell.isWalkable) {
              val continue = if (r == 0 || c == 0) true
              // 有拐角的情况
              else isWalkable(row, node.cell.column) && isWalkable(node.cell.row, column)
              if (continue) {
                val g = node.G + (if (r == 0 || c == 0) 10 else 14)
                val h = Math.abs(nextCell.row - to.row) + Math.abs(nextCell.column - to.column)
                surroundings ::= Node(node, nextCell, g, h)
              }
            }
          }
        }
      }
      surroundings
    }
  }

  case class Node(var parent: Node, cell: Cell, var G: Int, var H: Int, var checked: Boolean = false) {

    def F = G + H

  }

}