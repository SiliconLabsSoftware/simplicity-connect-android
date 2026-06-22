package com.siliconlabs.bledemo.features.scan.browser.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.features.scan.browser.adapters.LogAdapter.LogViewHolder
import com.siliconlabs.bledemo.features.scan.browser.models.logs.Log
import com.siliconlabs.bledemo.R

class LogAdapter(private var logList: List<Log>) : RecyclerView.Adapter<LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var tvTime = itemView.findViewById(R.id.tv_log_time) as TextView
        private var tvInfo = itemView.findViewById(R.id.tv_log_info) as TextView
        private val logContainer = itemView.findViewById<LinearLayout>(R.id.log_container)

        fun bind(log: Log, position: Int) {
            logContainer.setBackgroundColor(itemView.context.getColor(
                    if (position.rem(2) == 0) R.color.silabs_alternate_log_color_p2
                    else R.color.silabs_alternate_log_color_p1
            ))
            tvInfo.text = log.logInfo
            tvTime.text = log.logTime
        }
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logList[position]
        holder.bind(log, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_log, parent, false)
        return LogViewHolder(view)
    }

    override fun getItemCount(): Int {
        return logList.size
    }

    fun setLogList(logList: List<Log>) {
        this.logList = logList
    }
}
