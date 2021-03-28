package io.moyuru.timetablelayoutsample.decoration

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import io.moyuru.timetablelayout.decoration.ColumnNameDecoration
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayoutsample.R
import io.moyuru.timetablelayoutsample.model.Period

class NameDecoration(context: Context, private val timeTableItems: List<TimetableLayoutManager.TimeTableItem>,columnCount:Int, private val nameList:List<String>) :
  ColumnNameDecoration(
    columnCount,
    context.resources.getDimensionPixelSize(R.dimen.columnWidth),
    context.resources.getDimensionPixelSize(R.dimen.stageLabelHeight),
    context.resources.getDimension(R.dimen.stageLabelTextSize),
    Color.BLACK,
    ContextCompat.getColor(context, R.color.white)
  ) {

  override fun getColumnNumber(position: Int): Int = timeTableItems[position].columnNumber


  override fun getColumnName(columnNumber: Int): String = nameList[columnNumber]
}