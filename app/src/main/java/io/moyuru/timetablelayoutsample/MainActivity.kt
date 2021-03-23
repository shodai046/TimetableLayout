package io.moyuru.timetablelayoutsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.jakewharton.threetenabp.AndroidThreeTen
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayoutsample.databinding.ActivityMainBinding
import io.moyuru.timetablelayoutsample.decoration.ProgramTimeLabelDecoration
import io.moyuru.timetablelayoutsample.decoration.StageNameDecoration
import io.moyuru.timetablelayoutsample.item.ProgramItem
import io.moyuru.timetablelayoutsample.item.SpaceItem
import io.moyuru.timetablelayoutsample.model.EmptyPeriod
import io.moyuru.timetablelayoutsample.model.Period
import io.moyuru.timetablelayoutsample.model.Program
import io.moyuru.timetablelayoutsample.model.createPrograms
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset


class Event(var eventName:String = "",
            var startTime:Long = 0,
            var endTime:Long = 0){}

class MainActivity : AppCompatActivity() {

  private val binding by lazy {
    DataBindingUtil.setContentView<ActivityMainBinding>(
      this,
      R.layout.activity_main
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AndroidThreeTen.init(this)

    val adapter = GroupAdapter<ViewHolder>()

    //リスト作成
    val eventList = mutableListOf<Event>()
    eventList.add(Event("Fishing",
      LocalDateTime.of(2019, 6, 1, 12, 0).toEpochSecond(ZoneOffset.UTC) * 1000,
      LocalDateTime.of(2019, 6, 1, 14, 0).toEpochSecond(ZoneOffset.UTC) * 1000))

    //マップ作成
    val eventSchedule:MutableMap<String,MutableList<Event>> = mutableMapOf<String, MutableList<Event>>()
    eventSchedule.put("Yano", eventList)

    //高さを取得
    val heightPerMin = resources.getDimensionPixelSize(R.dimen.heightPerMinute)
    //区切り線を入れる
    binding.recyclerView.addItemDecoration(ProgramTimeLabelDecoration(this, periods, heightPerMin))
    /*//区切り線を入れる
    binding.recyclerView.addItemDecoration(
      StageNameDecoration(this, periods, periods.distinctBy { it.stageNumber }.size)
    )*/

    //レイアウトをセット
    binding.recyclerView.layoutManager =
      TimetableLayoutManager(
        resources.getDimensionPixelSize(R.dimen.columnWidth),
        heightPerMin
      ) {
        val period = periods[it]
        TimetableLayoutManager.PeriodInfo(
          period.startAt,
          period.endAt,
          period.stageNumber
        )
      }
    binding.recyclerView.adapter = adapter
    periods.map {
      when (it) {
        is EmptyPeriod -> SpaceItem()
        is Program -> ProgramItem(it)
        else -> return
      }
    }.let(adapter::update)
  }
}