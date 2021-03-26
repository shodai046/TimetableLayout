package io.moyuru.timetablelayoutsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.jakewharton.threetenabp.AndroidThreeTen
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager.Event
import io.moyuru.timetablelayoutsample.adapter.TimeTableAdapter
import io.moyuru.timetablelayoutsample.databinding.ActivityMainBinding
import org.threeten.bp.ZoneOffset
import java.time.LocalTime


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
    eventList.add(
      Event(
        "Fishing",
        LocalTime.of(2, 0),
        LocalTime.of(6, 0)
      )
    )
    eventList.add(
      Event(
        "Night",
        LocalTime.of(7, 0),
        LocalTime.of(12, 0)
      )
    )
    eventList.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    //マップ作成
    val eventSchedule:MutableMap<String,MutableList<Event>> = mutableMapOf<String, MutableList<Event>>()
    eventSchedule.put("Yano", eventList)

    //高さを取得
    val heightPerMin = resources.getDimensionPixelSize(R.dimen.heightPerMinute)
    //区切り線を入れる
    //binding.recyclerView.addItemDecoration(ProgramTimeLabelDecoration(this, periods, heightPerMin))
    /*//区切り線を入れる
    binding.recyclerView.addItemDecoration(
      StageNameDecoration(this, periods, periods.distinctBy { it.stageNumber }.size)
    )*/

    //レイアウトをセット
    binding.recyclerView.layoutManager =
      TimetableLayoutManager(
        resources.getDimensionPixelSize(R.dimen.columnWidth),
        heightPerMin,
        eventSchedule
      )
    binding.recyclerView.adapter = TimeTableAdapter(this,eventSchedule)
  }
}