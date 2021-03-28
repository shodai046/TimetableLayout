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
import io.moyuru.timetablelayoutsample.decoration.NameDecoration
import io.moyuru.timetablelayoutsample.decoration.TimeDecoration
import org.threeten.bp.LocalTime


class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    AndroidThreeTen.init(this)

    val adapter = GroupAdapter<ViewHolder>()

    //リスト作成
    val eventListA = mutableListOf<Event>()
    eventListA.add(
      Event(
        "Night",
        LocalTime.of(2, 20),
        LocalTime.of(4, 40)
      )
    )
    eventListA.add(
      Event(
        "Fishing",
        LocalTime.of(6, 0),
        LocalTime.of(8, 0)
      )
    )
    eventListA.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    val eventListB = mutableListOf<Event>()
    eventListB.add(
      Event(
        "Night",
        LocalTime.of(2, 0),
        LocalTime.of(4, 0)
      )
    )
    eventListB.add(
      Event(
        "Fishing",
        LocalTime.of(6, 0),
        LocalTime.of(8, 0)
      )
    )
    eventListB.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    val eventListC = mutableListOf<Event>()
    eventListC.add(
      Event(
        "Night",
        LocalTime.of(2, 0),
        LocalTime.of(4, 0)
      )
    )
    eventListC.add(
      Event(
        "Fishing",
        LocalTime.of(6, 0),
        LocalTime.of(8, 0)
      )
    )
    eventListC.add(
      Event(
        "Morning",
        LocalTime.of(14, 0),
        LocalTime.of(17, 0)
      )
    )

    eventListC.add(
      Event(
        "Morning",
        LocalTime.of(18, 20),
        LocalTime.of(23, 50)
      )
    )


    val nameList = mutableListOf("Yano","Tanaka","Yamamoto")

    //マップ作成
    val eventSchedule:MutableMap<String,MutableList<Event>> = mutableMapOf<String, MutableList<Event>>()
    eventSchedule[nameList[0]] = eventListA
    eventSchedule[nameList[1]] = eventListB
    eventSchedule[nameList[2]] = eventListC



    val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

    //高さを取得
    val heightPerMin = resources.getDimensionPixelSize(R.dimen.heightPerMinute)

    val timetableLayoutManager = TimetableLayoutManager(
      resources.getDimensionPixelSize(R.dimen.columnWidth),
      heightPerMin,
      eventSchedule
    )

    //TimeTableの生成
    val timeTable = timetableLayoutManager.createTimeTable()

    //区切り線を入れる
    recyclerView.addItemDecoration(TimeDecoration(this, eventSchedule, heightPerMin))
    //区切り線を入れる
    recyclerView.addItemDecoration(NameDecoration(this, timeTable, eventSchedule.size, nameList))


    recyclerView.adapter = TimeTableAdapter(this,timeTable)


    //レイアウトをセット
    recyclerView.layoutManager = timetableLayoutManager
  }
}