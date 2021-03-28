package io.moyuru.timetablelayoutsample.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager
import io.moyuru.timetablelayout.layoutmanager.TimetableLayoutManager.TimeTableItem
import io.moyuru.timetablelayoutsample.R

class TimeTableAdapter(private val context:Context, private val mTimeTableItems: MutableList<TimeTableItem>) :
    RecyclerView.Adapter<TimeTableAdapter.TimeTableViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeTableViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val mView = layoutInflater.inflate(R.layout.item_program,parent, false)

        return TimeTableViewHolder(mView)
    }

    override fun onBindViewHolder(holder: TimeTableViewHolder, position: Int) {
        if(mTimeTableItems[position] is TimetableLayoutManager.Margin){
            //holder.layout.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
        }else if(mTimeTableItems[position] is TimetableLayoutManager.Event){
            holder.layout.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
            holder.eventNameTextView.text = (mTimeTableItems[position] as TimetableLayoutManager.Event).eventName
        }
    }

    override fun getItemCount(): Int {
        return mTimeTableItems.size
    }


    class TimeTableViewHolder(view:View) :
        RecyclerView.ViewHolder(view) {
            val layout: ConstraintLayout = view.findViewById(R.id.recycler_view_item)
            val eventNameTextView:TextView = view.findViewById(R.id.text_view_event_name)
    }
}