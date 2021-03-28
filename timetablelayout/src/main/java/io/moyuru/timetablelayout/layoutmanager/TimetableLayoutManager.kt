package io.moyuru.timetablelayout.layoutmanager

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import androidx.core.util.forEach
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.recyclerview.widget.RecyclerView.State
import io.moyuru.timetablelayout.BuildConfig
import io.moyuru.timetablelayout.adapterPosition
import io.moyuru.timetablelayout.getOrPut
import kotlin.math.max
import kotlin.math.min
import org.threeten.bp.LocalTime


class TimetableLayoutManager(
  private val columnWidth: Int,
  private val heightPerMinute: Int,
  private val eventSchedule:MutableMap<String,MutableList<Event>>
) : RecyclerView.LayoutManager() {

  companion object {
    private const val NOT_SET = -1
  }

  abstract class TimeTableItem(
    open var startTime: LocalTime,
    open var endTime:LocalTime,
    open var columnNumber: Int = NOT_SET,
    open var adapterPosition: Int = NOT_SET,
    open var positionInColumn: Int = NOT_SET)

  class Event(var eventName:String = "",
              override var startTime: LocalTime,
              override var endTime:LocalTime,
              override var columnNumber: Int = NOT_SET,
              override var adapterPosition: Int = NOT_SET,
              override var positionInColumn: Int = NOT_SET):TimeTableItem(startTime, endTime, columnNumber, adapterPosition, positionInColumn)

  class Margin(override var startTime: LocalTime,
               override var endTime:LocalTime,
               override var columnNumber: Int = NOT_SET,
               override var adapterPosition: Int = NOT_SET,
               override var positionInColumn: Int = NOT_SET):TimeTableItem(startTime, endTime, columnNumber, adapterPosition, positionInColumn)

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
  private val timeTableItems = ArrayList<TimeTableItem>()

  //(Int, ArrayList<Period>) 一列ごとにPeriodの情報を保持
  private val columns = SparseArray<ArrayList<TimeTableItem>>()
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

    return dx
  }

  private fun calculateVerticallyScrollAmount(dy: Int): Int {
    return if (dy > 0) {
      val bottomView = findBottomView() ?: return 0
      val bottom = getDecoratedBottom(bottomView)
      if (bottom == parentBottom) 0 else min(dy, bottom - parentBottom)
    } else {
      val topView = findTopView() ?: return 0
      val top = getDecoratedTop(topView)
      if (top == parentTop) 0 else max(dy, top - parentTop)
    }
  }

  /**
   * left rightはめり込む
   */
  private fun calculateHorizontallyScrollAmount(dx: Int, left: Int, right: Int): Int {
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
    event: TimeTableItem,
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

  //一行分、レイアウトに配置する
  private fun addColumn(
    startEvent: TimeTableItem,
    offsetX: Int,
    startY: Int,
    isAppend: Boolean,
    recycler: Recycler
  ): Int {
    //Columnの列数を取得
    val columnNum = startEvent.columnNumber
    val timeTableColumn = columns[columnNum] ?: return 0
    val direction = if (isAppend) Direction.RIGHT else Direction.LEFT
    var offsetY = startY
    var columnWidth = 0
    for (i in startEvent.positionInColumn until timeTableColumn.size) {
      val timeTableItem = timeTableColumn[i]
      //Eventを一つずつレイアウトに配置する
      val (width, height) = addPeriod(timeTableItem, direction, offsetX, offsetY, recycler)

      //高さ方向のoffsetを更新
      offsetY += height
      //幅についてはそのままreturnする
      columnWidth = width

      if (i == startEvent.positionInColumn) anchor.top.put(columnNum, timeTableItem.adapterPosition)
      anchor.bottom.put(columnNum, timeTableItem.adapterPosition)
      //if (offsetY > parentBottom) break
    }
    return columnWidth
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

  private fun measureChild(view: View, event: TimeTableItem) {
    val lp = view.layoutParams as RecyclerView.LayoutParams
    lp.width = columnWidth
    lp.height = heightPerMinute*(event.endTime.toIntTime() -event.startTime.toIntTime())/1000

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
      val timeTableItem = timeTableItems[view.adapterPosition]
      //取得したperiodが一番左、もしくは右列のものだったらviewをreturnする
      if (timeTableItem.columnNumber == columnNumber) return view
    }
    return null
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

  //列の計算
  //
  private fun calculateColumns() {
    //全部リセット
    events.clear()
    columns.clear()

    timeTableItems.forEach {
      val column:ArrayList<TimeTableItem> = columns.getOrPut(it.columnNumber){ArrayList()}
      column.add(it)
    }
  }

  private fun Int.isFirstColumn() = this == 0

  private fun Int.isLastColumn() = this == columns.size() - 1

  private fun LocalTime.toIntTime():Int= this.hour*60*60 + this.minute *60

  private fun logw(log: String) {
    if (BuildConfig.DEBUG) Log.w(TimetableLayoutManager::class.java.simpleName, log)
  }

  fun createTimeTable() : MutableList<TimeTableItem>{
    var columnNumber = 0
    var adapterPosition = 0
    var positionInColumn = 0

    for(events in eventSchedule){
      //eventListを取得
      val eventList = events.value
      //previousEventTImeをセット（リセット）
      var previousEventTime = LocalTime.of(0,0)

      //一つずつEventを見ていく
      for(event in eventList){
        //Margin発生
        if(event.startTime > previousEventTime){
          //Marginを計算
          var margin =event.startTime.toIntTime() - previousEventTime.toIntTime()
          //Marginがなくなるまでループ
          while (margin > 0){
            val plusMinutes = if(previousEventTime.minute!=60)(60-previousEventTime.minute).toLong() else 60
            //Marginが1時間以上ある場合
            if(margin >=3600){
              timeTableItems.add(Margin(previousEventTime, previousEventTime.plusMinutes(plusMinutes),columnNumber,adapterPosition,positionInColumn))
            } else {
              timeTableItems.add(Margin(previousEventTime, event.startTime,columnNumber,adapterPosition,positionInColumn))
            }
            previousEventTime = previousEventTime.plusMinutes(plusMinutes)
            adapterPosition++
            positionInColumn++
            margin -= 3600
          }
        }
        //Eventを追加
        event.columnNumber = columnNumber
        event.adapterPosition = adapterPosition
        event.positionInColumn = positionInColumn
        timeTableItems.add(event)

        previousEventTime = event.endTime
        adapterPosition++
        positionInColumn++

        //columnの中で最後のEventの場合
        if(event == eventList.last()){
          //Margin発生
          if(previousEventTime <= lastEndTime) {
            //Marginを計算
            var margin = lastEndTime.toIntTime() - previousEventTime.toIntTime()
            //Marginがなくなるまでループ
            while (margin > 0) {
              //加算分のminutesを計算
              val plusMinutes =
                if (previousEventTime.minute != 60) (60 - previousEventTime.minute).toLong() else 60
              if (margin >= 3600) {
                //Marginが1時間以上の場合
                timeTableItems.add(
                  Margin(
                    previousEventTime,
                    previousEventTime.plusMinutes(plusMinutes),
                    columnNumber,
                    adapterPosition,
                    positionInColumn
                  )
                )
              } else {
                //Marginが1時間未満の場合
                timeTableItems.add(
                  Margin(
                    previousEventTime,
                    lastEndTime,
                    columnNumber,
                    adapterPosition,
                    positionInColumn
                  )
                )
              }
              previousEventTime = previousEventTime.plusMinutes(plusMinutes)
              adapterPosition++
              positionInColumn++

              margin -= 3600
            }
          }
        }
      }
      columnNumber++
      positionInColumn = 0
    }
    return timeTableItems
  }
}