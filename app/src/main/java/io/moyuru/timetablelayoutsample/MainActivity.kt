package io.moyuru.timetablelayoutsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.threetenabp.AndroidThreeTen
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager.Event
import io.moyuru.timetablelayoutsample.adapter.TimeTableAdapter
import io.moyuru.timetablelayoutsample.decoration.ProgramTimeLabelDecoration
import io.moyuru.timetablelayoutsample.decoration.TimeDecoration
import java.time.LocalTime


class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    AndroidThreeTen.init(this)

    val adapter = GroupAdapter<ViewHolder>()

    //リスト作成
    val eventList = mutableListOf<Event>()
    eventList.add(
      Event(
        "Night",
        LocalTime.of(7, 0),
        LocalTime.of(8, 0)
      )
    )
    eventList.add(
      Event(
        "Fishing",
        LocalTime.of(2, 0),
        LocalTime.of(9, 0)
      )
    )
    eventList.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    eventList.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    eventList.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
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
    eventSchedule.put("Tanaka", eventList)

    val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

    //高さを取得
    val heightPerMin = resources.getDimensionPixelSize(R.dimen.heightPerMinute)
    //区切り線を入れる
    recyclerView.addItemDecoration(TimeDecoration(this, eventSchedule, heightPerMin))
    /*//区切り線を入れる
    binding.recyclerView.addItemDecoration(
      StageNameDecoration(this, periods, periods.distinctBy { it.stageNumber }.size)
    )*/

    recyclerView.adapter = TimeTableAdapter(this,eventSchedule)

    val timetableLayoutManager = TimetableLayoutManager(
      resources.getDimensionPixelSize(R.dimen.columnWidth),
      heightPerMin,
      eventSchedule
    )

    //TimeTableLayoutの初期化
    timetableLayoutManager.initializeTimeTable(LocalTime.of(0, 0), LocalTime.of(23, 59), 1)

    //レイアウトをセット
    recyclerView.layoutManager = timetableLayoutManager
  }
}