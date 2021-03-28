package io.moyuru.timetablelayoutsample.decoration

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import io.moyuru.timetablelayout.decoration.ColumnNameDecoration
import io.moyuru.timetablelayout.decoration.TimeLabelDecoration
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayoutsample.R
import io.moyuru.timetablelayoutsample.model.Period
import io.moyuru.timetablelayoutsample.model.Program
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

class TimeDecoration(context: Context, heightPerMin: Int) :
  TimeLabelDecoration(
    context.resources.getDimensionPixelSize(R.dimen.timeLabelWidth),
    heightPerMin,
    context.resources.getDimension(R.dimen.timeLabelTextSize),
    Color.BLACK,
    ContextCompat.getColor(context, R.color.white)
  ) {

  private val formatter = DateTimeFormatter.ofPattern("HH:mm")

  /*override fun canDecorate(position: Int): Boolean = periods.getOrNull(position) is Program

  override fun getStartUnixMillis(position: Int): Long = periods.getOrNull(position)?.startAt ?: 0*/

  override fun formatUnixMillis(time: Int): String {
    return "$time:00"
  }
}