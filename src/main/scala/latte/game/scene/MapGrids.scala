package latte.game.scene

import java.io.FileInputStream

/**
 * Created by linyuhe on 2018/11/23.
 */
object MapGrids {

  def apply(file: String): MapGrids = {
    val reader = new FileInputStream(file)
    try {
      val bytes = new Array[Byte](4)
      reader.read(bytes)
      val startX = toInt(bytes) / 100f // 起点坐标x
      reader.read(bytes)
      val startZ = toInt(bytes) / 100f // 起点坐标z
      reader.read(bytes)
      val size = toInt(bytes) / 100f // grid的大小
      reader.read(bytes)
      val rows = toInt(bytes) // 行
      reader.read(bytes)
      val columns = toInt(bytes) // 列

      val mapGrids = new MapGrids(startX, startZ, size, rows, columns)
      val grids = Array.ofDim[MapGrid](rows, columns)
      for (row <- 0 until rows) {
      for (column <- 0 until columns) {
          val `type` = reader.read
          grids(row)(column) = new MapGrid(mapGrids, row, column, `type`)
        }
      }
      mapGrids.setGrids(grids)
      mapGrids
    } finally {
      reader.close()
    }
  }

  private def toInt(bytes: Array[Byte]) = {
    (bytes(0) & 0xff) | (bytes(1) & 0xff) << 8 | (bytes(2) & 0xff) << 16 | (bytes(3) & 0xff) << 24
  }
}

class MapGrids(val startX: Double, val startZ: Double, val size: Double, val rows: Int, val columns: Int) {

  private var grids: Array[Array[MapGrid]] = null

  def setGrids(grids: Array[Array[MapGrid]]): Unit = {
    this.grids = grids
  }

  def getGrid(pos: Vector2): MapGrid = {
    val row = ((pos.z - startZ) / size).toInt
    val column = ((pos.x - startX) / size).toInt

    if (row >= 0 && row < rows && column >= 0 && column < columns) {
      grids(row)(column)
    } else {
      null
    }
  }

  def getGrid(row: Int, column: Int) = grids(row)(column)

  // 是否可达
  def isWalkable(coord: Vector2) = getGrid(coord).isWalkable

  // A*寻路
  def findPath(start: MapGrid, end: MapGrid) = new AStarPath(this, start, end).find()
}

class MapGrid(val grids: MapGrids, val row: Int, val column: Int, val `type`: Int) {

  private var areaId: Int = 0

  def setAreaId(areaId: Int) = this.areaId = areaId

  def getAreaId = areaId

  def isWalkable = `type` == 1 || `type` == 2

  def center = {
    val halfSize = grids.size / 2
    val x = grids.startX + column * grids.size + halfSize
    val z = grids.startZ + row * grids.size + halfSize
    
    Vector2(x, z)
  }

  override def toString() = s"($row, $column)"

}
