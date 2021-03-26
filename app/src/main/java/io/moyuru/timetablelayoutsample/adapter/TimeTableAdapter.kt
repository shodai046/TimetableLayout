package io.moyuru.timetablelayoutsample.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager.Event
import io.moyuru.timetablelayoutsample.R

class TimeTableAdapter(private val context:Context, private val mPeriods: MutableMap<String,MutableList<Event>>) :
    RecyclerView.Adapter<TimeTableAdapter.TimeTableViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeTableViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val mView = layoutInflater.inflate(R.layout.item_program,parent, false)

        return TimeTableViewHolder(mView)
    }

    override fun onBindViewHolder(holder: TimeTableViewHolder, position: Int) {

    }

    override fun getItemCount(): Int {
        var size = 0

        for(events in mPeriods){
            val eventList = events.value
            size += eventList.size
        }
        return size
    }


    class TimeTableViewHolder(private val view:View) :
        RecyclerView.ViewHolder(view) {
    }
}