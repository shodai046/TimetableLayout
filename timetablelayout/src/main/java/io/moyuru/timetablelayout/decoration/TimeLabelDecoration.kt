package io.moyuru.timetablelayout.decoration

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.ColorInt
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import io.moyuru.timetablelayout.drawTextAtCenter
import io.moyuru.timetablelayout.layoutPosition
import java.util.concurrent.TimeUnit

abstract class TimeLabelDecoration(
  private val width: Int,
  private val heightPerMinute: Int,
  private val timeTextSize: Float,
  @ColorInt private val timeTextColor: Int,
  @ColorInt private val backgroundColor: Int
) : RecyclerView.ItemDecoration() {

  private val textPaint = Paint().apply {
    color = timeTextColor
    isAntiAlias = true
    textSize = timeTextSize
  }

  private val backgroundPaint = Paint().apply { color = backgroundColor }

  /*private val textHeight by lazy {

    // val text = formatUnixMillis(System.currentTimeMillis())
    Rect().apply { textPaint.getTextBounds(text, 0, text.lastIndex, this) }.height()
  }*/

  //アイテムが描画された後に呼び出される
  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    super.onDrawOver(c, parent, state)

    //矩形を描画
    c.drawRect(0f, 0f, width.toFloat(), c.height.toFloat(), backgroundPaint)

    //adapterをセット
    val adapter = parent.adapter ?: return
    if (parent.childCount < 0) return

    //adapterからStartAt取得
    //val startAtList:List<Long> = (0 until adapter.itemCount).map(this::getStartUnixMillis)

    val base = parent.children.filter { it.top <= parent.paddingTop }.minBy { it.top } ?: return
    //val baseEpochMillis = startAtList.getOrNull(base.layoutPosition) ?: return

    //val height = parent.height
    //val num = height/(60*heightPerMinute)


    for(i in 0 .. 24){
      val gap = heightPerMinute* i*60*60/1000
      //val gap = TimeUnit.MILLISECONDS.toMinutes((i+2)*60+baseEpochMillis - baseEpochMillis) * heightPerMinute
      val top = base.top + gap
      c.drawTextAtCenter(
        formatUnixMillis(i),
        //"a",
        //Rect(0, top.toInt(), width, (top + textHeight).toInt()),
        Rect(0, top.toInt(), width, (top + 32).toInt()),
        textPaint
      )
    }
  }

 /* protected abstract fun canDecorate(position: Int): Boolean

  protected abstract fun getStartUnixMillis(position: Int): Long*/

  protected abstract fun formatUnixMillis(time:Int): String
}