package com.limpu.hitax.ui.subject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.databinding.ItemSubjectAgentTraceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubjectAgentTraceListAdapter : RecyclerView.Adapter<SubjectAgentTraceListAdapter.TraceHolder>() {
    private val items = mutableListOf<AgentTraceEvent>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class TraceHolder(val binding: ItemSubjectAgentTraceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraceHolder {
        val binding = ItemSubjectAgentTraceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return TraceHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TraceHolder, position: Int) {
        val event = items[position]
        holder.binding.traceStage.text = event.stage
        holder.binding.traceMessage.text = event.message
        holder.binding.tracePayload.text = event.payload
        holder.binding.traceTime.text = timeFormat.format(Date(event.timestampMs))
    }

    fun append(event: AgentTraceEvent, maxSize: Int = 30) {
        items.add(event)
        notifyItemInserted(items.size - 1)
        if (items.size > maxSize) {
            items.removeAt(0)
            notifyItemRemoved(0)
        }
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        notifyDataSetChanged()
    }
}
