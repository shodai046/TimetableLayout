package io.moyuru.timetablelayout.layoutmanager

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import androidx.core.util.forEach
import androidx.core.util.size
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.recyclerview.widget.RecyclerView.State
import io.moyuru.timetablelayout.BuildConfig
import io.moyuru.timetablelayout.adapterPosition
import io.moyuru.timetablelayout.getOrPut
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min


class TimetableLayoutManager(
  private val columnWidth: Int,
  private val heightPerMinute: Int,
  private val eventSchedule:MutableMap<String,MutableList<Event>>
) : RecyclerView.LayoutManager() {

  companion object {
    private const val NO_TIME = -1
    private const val NOT_SET = -1
  }

  class Event(var eventName:String = "",
              var startTime: LocalTime,
              var endTime:LocalTime,
              var columnNumber: Int = NOT_SET,
              var adapterPosition: Int = NOT_SET,
              var positionInColumn: Int = NOT_SET)

  private class Anchor {
    val top = SparseIntArray()
    val bottom = SparseIntArray()
    var leftColumn = NO_POSITION
    var rightColumn = NO_POSITION

    fun reset() {
      top.clear()
      bottom.clear()
      leftColumn = NO_POSITION
      rightColumn = NO_POSITION
    }
  }

  //方向列挙体
  private enum class Direction {
    LEFT, TOP, RIGHT, BOTTOM
  }

  private data class SavedState(val position: Int, val left: Int, val top: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
      parcel.readInt(),
      parcel.readInt(),
      parcel.readInt()
    )

    //Parcel系メソッド
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeInt(position)
      parcel.writeInt(left)
      parcel.writeInt(top)
    }

    //Parcel系メソッド
    override fun describeContents(): Int {
      return 0
    }

    companion object CREATOR : Parcelable.Creator<SavedState> {
      override fun createFromParcel(parcel: Parcel): SavedState {
        return SavedState(parcel)
      }

      override fun newArray(size: Int): Array<SavedState?> {
        return arrayOfNulls(size)
      }
    }
  }

  private val parentLeft get() = paddingLeft
  private val parentTop get() = paddingTop
  private val parentRight get() = width - paddingRight
  private val parentBottom get() = height - paddingBottom

  private val events = ArrayList<Event>()

  //(Int, ArrayList<Period>) 一列ごとにPeriodの情報を保持
  private val columns = SparseArray<ArrayList<Event>>()
  private val anchor = Anchor()

  private var firstStartTime = LocalTime.of(0, 0)
  private var lastEndTime = LocalTime.of(23, 59)
  private var durationHour: Int = NOT_SET

  private var pendingScrollPosition = NO_POSITION
  private var savedState: SavedState? = null
  var shouldRecycleChildrenOnDetach = false

  //デフォルトのLayoutParamsを生成
  override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
    return RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT)
  }

  //復帰時にsavedStateを取得
  override fun onRestoreInstanceState(state: Parcelable?) {
    savedState = (state as? SavedState)
  }

  //savedStateに保存
  override fun onSaveInstanceState(): Parcelable? {
    if (childCount == 0) return null

    val view = findFirstVisibleView() ?: return null
    return SavedState(
      view.adapterPosition,
      getDecoratedLeft(view),
      getDecoratedTop(view)
    )
  }

  override fun onDetachedFromWindow(view: RecyclerView, recycler: Recycler) {
    super.onDetachedFromWindow(view, recycler)
    if (shouldRecycleChildrenOnDetach) {
      removeAndRecycleAllViews(recycler)
      recycler.clear()
    }
  }

  //layoutの初期化を行う
  //adapterがセットされたときに一度だけ呼ばれる
  override fun onLayoutChildren(recycler: Recycler, state: State) {
    //itemが一つもなければreturn
    if (itemCount == 0) {
      detachAndScrapAttachedViews(recycler)
      events.clear()
      columns.clear()
      anchor.reset()
      savedState = null
      return
    }

    //columns, firstStartTime, lastStartUnixMinを生成
    calculateColumns()

    //NO_POSITIONではない場合
    if (pendingScrollPosition != NO_POSITION) {
      anchor.reset()
      //既に配置されているViewの廃棄
      detachAndScrapAttachedViews(recycler)
      events.getOrNull(pendingScrollPosition)?.let { event ->
        anchor.leftColumn = event.columnNumber
        //fillHorizontalChunk(event.columnNumber, parentLeft, parentTop, event, true, recycler)
        fixBottomLayoutGap(recycler)
      }
      return
    }
    //初回は全部nullになるはず
    val firstVisibleView = findFirstVisibleView()
    val restoredOffsetX = savedState?.left ?: firstVisibleView?.let(this::getDecoratedLeft)
    val restoredOffsetY = savedState?.top ?: firstVisibleView?.let(this::getDecoratedTop)
    val restoredPeriod = (savedState?.position ?: anchor.top.get(anchor.leftColumn, -1)).let(events::getOrNull)

    //既に配置されているViewの廃棄
    anchor.reset()
    detachAndScrapAttachedViews(recycler)

    if (restoredOffsetX != null && restoredOffsetY != null && restoredPeriod != null) {
      anchor.leftColumn = restoredPeriod.columnNumber
      //fillHorizontalChunk(restoredPeriod.columnNumber, restoredOffsetX, restoredOffsetY, restoredPeriod, true, recycler)
    } else {
      //0が入るはず
      anchor.leftColumn = columns.keyAt(0)
      val columnCount = columns.size()
      //paddingTopとPaddingLeft
      //viewに設定されているmarginがあればoffsetが設定される
      val offsetY = parentTop
      var offsetX = parentLeft
      //列ごとに計算
      for (columnNumber in 0 until columnCount) {
        //列の中で一番最初のPeriodを渡す
        offsetX += addColumn(columns[columnNumber].first(), offsetX, offsetY, true, recycler)
        //一番左に表示されている列を取得、設定
        anchor.rightColumn = columnNumber
        if (offsetX > parentRight) break
      }
    }
  }

  override fun onLayoutCompleted(state: State?) {
    pendingScrollPosition = NO_POSITION
    savedState = null
  }

  override fun scrollToPosition(position: Int) {
    if (position < 0 || position >= itemCount) return
    pendingScrollPosition = position
    requestLayout()
  }

  override fun canScrollVertically() = true

  override fun canScrollHorizontally() = true

  override fun scrollVerticallyBy(dy: Int, recycler: Recycler, state: State): Int {
    if (dy == 0) return 0

    val actualDy = calculateVerticallyScrollAmount(dy)
    if (actualDy == 0) return 0
    offsetChildrenVertical(-actualDy)
    if (actualDy > 0) {
      // recycle
      recycleTop(recycler)

      // append
      /*anchor.bottom.forEach { columnNum, position ->
        val view = findViewByPosition(position) ?: return@forEach
        val bottom = getDecoratedBottom(view)
        if (bottom < parentBottom) {
          val left = getDecoratedLeft(view)
          val period = periods.getOrNull(position) ?: return@forEach
          val nextPeriod = columns.get(columnNum).getOrNull(period.positionInColumn + 1) ?: return@forEach
          addPeriodsToColumn(nextPeriod, left, bottom, true, recycler)
        }
      }*/
    } else {
      // recycle
      recycleBottom(recycler)

      // prepend
      /*anchor.top.forEach { columnNum, position ->
        val view = findViewByPosition(position) ?: return@forEach
        val top = getDecoratedTop(view)
        if (top > parentTop) {
          val left = getDecoratedLeft(view)
          val period = periods.getOrNull(position) ?: return@forEach
          val nextPeriod = columns.get(columnNum).getOrNull(period.positionInColumn - 1) ?: return@forEach
          addPeriodsToColumn(nextPeriod, left, top, false, recycler)
        }
      }*/
    }
    return actualDy
  }

  override fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: State): Int {
    if (dx == 0) return 0

    //現在表示されている一番左と右の列のviewを取得
    val rightView = findRightView() ?: return 0
    val leftView = findLeftView() ?: return 0
    //実際の移動量を計算
    //getDecoratedLeft(leftView)…leftViewのleft
    //getDecoratedRight(rightView)…rightViewのright
    val actualDx = calculateHorizontallyScrollAmount(dx, getDecoratedLeft(leftView), getDecoratedRight(rightView))
    //移動量0なら変更はなし
    if (actualDx == 0) return 0

    //全てのViewを一括で水平移動
    offsetChildrenHorizontal(-actualDx)
    //すでに移動した後なので、actualDxが反映済み
    if (actualDx > 0) {
      // recycle（右側にリサイクル）
      //左端のviewが完全に見えなくなったら
      if (getDecoratedRight(leftView) < parentLeft) recycleLeft(recycler)

      // append
      val right = getDecoratedRight(rightView)
      if (right < parentRight) {
        val topView = findTopView() ?: return 0
        val top = getDecoratedTop(topView)
        val topPeriod = events[topView.adapterPosition]
        val nextColumn = anchor.rightColumn.getNextColumn()
        //fillHorizontalChunk(nextColumn, right, top, topPeriod, true, recycler)
      }
    } else if (actualDx <= 0)  {
      // recycle（左側にリサイクル）
      //右端のviewが完全に見えなくなったら
      if (getDecoratedLeft(rightView) > parentRight) recycleRight(recycler)

      // prepend
      val left = getDecoratedLeft(leftView)
      if (left > parentLeft) {
        val topView = findTopView() ?: return 0
        val top = getDecoratedTop(topView)
        val topPeriod = events[topView.adapterPosition]
        val previousColumn = anchor.leftColumn.getPreviousColumn()
        //fillHorizontalChunk(previousColumn, left, top, topPeriod, false, recycler)
      }
    }

    return dx
  }

  private fun calculateVerticallyScrollAmount(dy: Int): Int {
    return if (dy > 0) {
      val bottomView = findBottomView() ?: return 0
      val event = events.getOrNull(bottomView.adapterPosition) ?: return 0
      val bottom = getDecoratedBottom(bottomView)
      if (event.startTime == lastEndTime) if (bottom == parentBottom) 0 else min(dy, bottom - parentBottom)
      else dy
    } else {
      val topView = findTopView() ?: return 0
      val event = events.getOrNull(topView.adapterPosition) ?: return 0
      val top = getDecoratedTop(topView)
      if (event.startTime == firstStartTime) if (top == parentTop) 0 else max(dy, top - parentTop)
      else dy
    }
  }

  /**
   * left rightはめり込む
   */
  private fun calculateHorizontallyScrollAmount(dx: Int, left: Int, right: Int): Int {
    //左列が最初の列、もしくは右列が最後の列以外ならそのまま移動量を返す
    //全列画面に入りきらない場合は絶対ここでreturn
    //if (!anchor.leftColumn.isFirstColumn()) return dx
    //if (!anchor.rightColumn.isLastColumn()) return dx

    //それ以外の場合
    return if (dx > 0) {
      //最終列の場合
      if (anchor.rightColumn.isLastColumn())
        if (right <= parentRight) 0 else min(dx, right - parentRight)
      else dx
    } else {
      //最初列の場合
      if (anchor.leftColumn.isFirstColumn())
        if (left >= parentLeft) 0 else max(dx, left - parentLeft)
      else dx
    }
  }

  private fun addPeriod(
    event: Event,
    direction: Direction,
    offsetX: Int,
    offsetY: Int,
    recycler: Recycler
  ): Pair<Int, Int> {
    //adapterPositionに対応するViewを返す
    val view = recycler.getViewForPosition(event.adapterPosition)
    //レイアウトにViewを追加
    addView(view)
    //子viewのサイズを計算
    measureChild(view, event)
    //子viewの幅、高さを取得
    val width = getDecoratedMeasuredWidth(view)
    val height = getDecoratedMeasuredHeight(view)
    //子Viewの座標を計算
    val left = if (direction == Direction.LEFT) offsetX - width else offsetX
    val top = if (direction == Direction.TOP) offsetY - height else offsetY
    val right = left + width
    val bottom = top + height
    //子Viewをレイアウトに配置
    layoutDecorated(view, left, top, right, bottom)
    return width to height
  }

  private fun addPeriodsToColumn(
    startPeriod: Event,
    offsetX: Int,
    startY: Int,
    isAppend: Boolean,
    recycler: Recycler
  ): Int {
    val column = columns.get(startPeriod.columnNumber) ?: return 0
    val direction = if (isAppend) Direction.BOTTOM else Direction.TOP
    var offsetY = startY
    val range = if (isAppend) startPeriod.positionInColumn until column.size else startPeriod.positionInColumn downTo 0
    for (i in range) {
      val period = column[i]
      //val (_, height) = addPeriod(period, direction, offsetX, offsetY, recycler)
      if (isAppend) {
        //anchor.bottom.put(period.columnNumber, period.adapterPosition)
        offsetY += height
        if (offsetY > parentBottom) return offsetY - startY
      } else {
        //anchor.top.put(period.columnNumber, period.adapterPosition)
        offsetY -= height
        if (offsetY < parentTop) return startY - offsetY
      }
    }
    return (offsetY - startY).absoluteValue
  }
  //一行分、レイアウトに配置する
  private fun addColumn(
    startEvent: Event,
    offsetX: Int,
    startY: Int,
    isAppend: Boolean,
    recycler: Recycler
  ): Int {
    //Columnの列数を取得
    val columnNum = startEvent.columnNumber
    val events = columns[columnNum] ?: return 0
    val direction = if (isAppend) Direction.RIGHT else Direction.LEFT
    var offsetY = startY
    var columnWidth = 0
    for (i in startEvent.positionInColumn until events.size) {
      val event = events[i]
      //Eventを一つずつレイアウトに配置する
      val (width, height) = addPeriod(event, direction, offsetX, offsetY, recycler)

      //高さ方向のoffsetを更新
      offsetY += height
      //幅についてはそのままreturnする
      columnWidth = width

      if (i == startEvent.positionInColumn) anchor.top.put(columnNum, event.adapterPosition)
      anchor.bottom.put(columnNum, event.adapterPosition)
      if (offsetY > parentBottom) break
    }
    return columnWidth
  }

  /*private fun fillHorizontalChunk(
    startColumnNum: Int,
    startX: Int,
    baseY: Int,
    basePeriod: Event,
    isAppend: Boolean,
    recycler: Recycler
  ): Int {
    val lastColumnNum = columns.size - 1
    val range = if (isAppend) {
      if (startColumnNum > 0) (startColumnNum..lastColumnNum) + (0 until startColumnNum)
      else startColumnNum..lastColumnNum
    } else {
      if (startColumnNum < lastColumnNum) (startColumnNum downTo 0) + (lastColumnNum downTo startColumnNum + 1)
      else (startColumnNum downTo 0)
    }

    var offsetX = startX
    val added = arrayListOf<Int>()
    for (nextColumnNum in range) {
      val startPeriod = calculateStartPeriodInColumn(nextColumnNum, baseY, basePeriod) ?: continue
      val offsetY = baseY + (startPeriod.startUnixMin - basePeriod.startUnixMin) * heightPerMinute
      //val width = addColumn(startPeriod, offsetX, offsetY, isAppend, recycler)
      added += nextColumnNum
      if (isAppend) {
        anchor.rightColumn = nextColumnNum
        offsetX += width
      } else {
        anchor.leftColumn = nextColumnNum
        offsetX -= width
      }
      if (isAppend && offsetX > parentRight) break
      if (!isAppend && offsetX < parentLeft) break
    }

    return (offsetX - startX).absoluteValue
  }*/

  private fun recycleTop(recycler: Recycler) {
    (anchor.leftColumn..anchor.rightColumn).forEach { columnNum ->
      val column = columns[columnNum]
      val top = events[anchor.top[columnNum]]
      val bottom = events[anchor.bottom[columnNum]]
      column.subList(top.positionInColumn, bottom.positionInColumn).forEach { period ->
        /*val view = findViewByPosition(period.adapterPosition) ?: return
        if (getDecoratedBottom(view) >= parentTop) return

        removeAndRecycleView(view, recycler)
        val belowPosition = column[period.positionInColumn + 1].adapterPosition
        anchor.top.put(columnNum, belowPosition)*/
      }
    }
  }

  private fun recycleBottom(recycler: Recycler) {
    (anchor.leftColumn..anchor.rightColumn).forEach { columnNum ->
      val column = columns[columnNum]
      val top = events[anchor.top[columnNum]]
      val bottom = events[anchor.bottom[columnNum]]
      column.subList(top.positionInColumn, bottom.positionInColumn).asReversed().forEach { period ->
        /*val view = findViewByPosition(period.adapterPosition) ?: return
        if (getDecoratedTop(view) <= parentBottom) return

        removeAndRecycleView(view, recycler)
        val abovePosition = column[period.positionInColumn - 1].adapterPosition
        anchor.bottom.put(columnNum, abovePosition)*/
      }
    }
  }

  private fun recycleLeft(recycler: Recycler) {
    val removed = arrayListOf<Int>()
    //最左~再右までのレンジを取得（例:0~3）
    val range = if (anchor.rightColumn > anchor.leftColumn) (anchor.leftColumn..anchor.rightColumn)
    //最左~再右までのレンジを取得（例:3~4 ＋ 0~1）
    else (anchor.leftColumn until columns.size()) + (0..anchor.rightColumn)
    range.forEach { columnNum ->
      //列数に対応するviewを取得
      val views = findViewsByColumn(columnNum)
      val view = views.firstOrNull() ?: return@forEach
      if (getDecoratedRight(view) >= parentLeft) return@forEach

      views.forEach { removeAndRecycleView(it, recycler) }
      //表示されていない列のリストに追加
      removed += columnNum
      //一番左の列を更新
      anchor.leftColumn = anchor.leftColumn.getNextColumn()
    }
  }

  private fun recycleRight(recycler: Recycler) {
    val range = if (anchor.leftColumn < anchor.rightColumn) (anchor.rightColumn downTo anchor.leftColumn)
    else (anchor.rightColumn downTo 0) + ((columns.size() - 1) downTo anchor.leftColumn)
    range.forEach { columnNum ->
      val views = findViewsByColumn(columnNum)
      val view = views.firstOrNull() ?: return@forEach
      if (getDecoratedLeft(view) <= parentRight) return@forEach

      views.forEach { removeAndRecycleView(it, recycler) }
      anchor.rightColumn = anchor.rightColumn.getPreviousColumn()
    }
  }

  private fun fixBottomLayoutGap(recycler: Recycler) {
    val bottomView = findBottomView() ?: return
    val bottomPeriod = events[bottomView.adapterPosition]
    val bottom = getDecoratedBottom(bottomView)
    if (bottom > parentBottom) return

    //val expectedGap = (lastEndTime - bottomPeriod.endUnixMin) * heightPerMinute
    val actualGap = (parentBottom - bottom)
    //offsetChildrenVertical(actualGap - expectedGap)
    anchor.top.forEach { columnNum, position ->
      val view = findViewByPosition(position) ?: return@forEach
      val top = getDecoratedTop(view)
      if (top > parentTop) {
        val left = getDecoratedLeft(view)
        //val anchorPeriod = periods.getOrNull(position) ?: return@forEach
        //val nextPeriod = columns.get(columnNum)
          //.getOrNull(anchorPeriod.positionInColumn - 1) ?: return@forEach
        //addPeriodsToColumn(nextPeriod, left, top, false, recycler)
      }
    }
  }

  private fun measureChild(view: View, event: Event) {
    val lp = view.layoutParams as RecyclerView.LayoutParams
    lp.width = columnWidth
    val endTime = event.endTime.hour *60 *60 + event.endTime.minute *60 +event.endTime.second
    val startTime = event.startTime.hour *60 *60 + event.startTime.minute *60 +event.startTime.second
    lp.height = (endTime-startTime)/1000* heightPerMinute

    val insets = Rect().apply { calculateItemDecorationsForChild(view, this) }
    //measureSpecの計算（横）
    val widthSpec = getChildMeasureSpec(
      width,
      widthMode,
      paddingLeft + paddingRight + insets.left + insets.right,
      lp.width,
      true
    )
    //measureSpecの計算（縦）
    val heightSpec = getChildMeasureSpec(
      height,
      heightMode,
      paddingTop + paddingBottom + insets.top + insets.bottom,
      lp.height,
      true
    )
    //子viewのサイズを計算させる
    view.measure(widthSpec, heightSpec)
  }

  private fun findTopView(): View? {
    var minTop: Int? = null
    var minView: View? = null
    anchor.top.forEach { _, position ->
      val view = findViewByPosition(position) ?: return@forEach
      val top = getDecoratedTop(view)
      if (minView == null) {
        minView = view
        minTop = top
        return@forEach
      }
      minTop?.let {
        if (top < it) {
          minView = view
          minTop = top
        }
      }
    }
    return minView
  }

  private fun findBottomView(): View? {
    var maxBottom: Int? = null
    var maxView: View? = null
    anchor.bottom.forEach { _, position ->
      val view = findViewByPosition(position) ?: return@forEach
      val bottom = getDecoratedBottom(view)
      if (maxView == null) {
        maxView = view
        maxBottom = bottom
        return@forEach
      }
      maxBottom?.let {
        if (bottom > it) {
          maxView = view
          maxBottom = bottom
        }
      }
    }
    return maxView
  }

  private fun findLeftView() = findViewByColumn(anchor.leftColumn)

  private fun findRightView() = findViewByColumn(anchor.rightColumn)

  /**
   * columnNumberに対応するviewを取得する
   */
  private fun findViewByColumn(columnNumber: Int): View? {
    //childCount…表示されているviewの数
    (0 until childCount).forEach { layoutPosition ->
      //表示されているviewをひとつずつ取得していく
      val view = getChildAt(layoutPosition) ?: return@forEach
      //取得したviewに対応するPeriodを取得
      val period = events[view.adapterPosition]
      //取得したperiodが一番左、もしくは右列のものだったらviewをreturnする
      if (period.columnNumber == columnNumber) return view
    }
    return null
  }

  private fun findViewsByColumn(columnNumber: Int): List<View> {
    return (0 until childCount).mapNotNull { layoutPosition ->
      val view = getChildAt(layoutPosition) ?: return@mapNotNull null
      val period = events[view.adapterPosition]
      if (period.columnNumber == columnNumber) view else null
    }
  }

  private fun findFirstVisibleView(): View? {
    if (childCount == 0) return null

    return (0 until childCount).asSequence()
      .mapNotNull(this::getChildAt)
      .filter { getDecoratedLeft(it) <= parentLeft }
      .minWith(Comparator { viewL, viewR ->
        getDecoratedTop(viewL) - getDecoratedTop(viewR)
      })
  }

  private fun calculateStartPeriodInColumn(columnNumber: Int, top: Int, topPeriod: Event): Event? {
    val periods = columns[columnNumber] ?: return null
    var maxTopPeriod: Event? = null
    /*periods.filter { it.startUnixMin <= topPeriod.endUnixMin && it.endUnixMin >= topPeriod.startUnixMin }
      .forEach { period ->
        val gapHeight = (period.startUnixMin - topPeriod.startUnixMin) * heightPerMinute
        if (top + gapHeight <= parentTop)
          maxTopPeriod = maxTopPeriod?.let { if (it.startUnixMin < period.startUnixMin) period else it } ?: period
      }*/
    return maxTopPeriod
  }

  //列の計算
  //
  private fun calculateColumns() {
    //全部リセット
    events.clear()
    columns.clear()

    var columnNum = 0
    var eventNum = 0
    var totalEventNum = 0

    //列ごとにEventListを取り出す
    eventSchedule.forEach { s, mutableList ->
      //EventList
      val column:ArrayList<Event> = columns.getOrPut(columnNum){ArrayList()}
      //EventListからEventを取り出す
      mutableList.forEach {
        var event = Event(it.eventName,it.startTime,it.endTime,columnNum,totalEventNum,eventNum)

        //イベントを追加
        events.add(event)
        column.add(event)
        eventNum++
        totalEventNum++
      }
      eventNum = 0
      columnNum++
    }
  }

  private fun Int.isFirstColumn() = this == 0

  private fun Int.isLastColumn() = this == columns.size() - 1

  private fun Int.getNextColumn() = if (this == columns.size - 1) 0 else this + 1

  private fun Int.getPreviousColumn() = if (this == 0) columns.size - 1 else this - 1

  private fun logw(log: String) {
    if (BuildConfig.DEBUG) Log.w(TimetableLayoutManager::class.java.simpleName, log)
  }
  
  fun initializeTimeTable(startTime :LocalTime, endTime :LocalTime, durationHour:Int){
    firstStartTime = startTime
    lastEndTime = endTime
    
  }
}