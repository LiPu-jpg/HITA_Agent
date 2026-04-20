package com.stupidtree.hitax.ui.main.agent

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.stupidtree.hitax.R
import com.stupidtree.hitax.databinding.ItemAgentChatMessageBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

class AgentChatMessageAdapter : RecyclerView.Adapter<AgentChatMessageAdapter.MessageHolder>() {
    private val items = mutableListOf<AgentChatMessage>()
    private var markwon: Markwon? = null

    class MessageHolder(val binding: ItemAgentChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    private fun getMarkwon(context: Context): Markwon {
        return markwon ?: Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .build()
            .also { markwon = it }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        val binding = ItemAgentChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return MessageHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val item = items[position]

        val layoutParams = holder.binding.messageCard.layoutParams as FrameLayout.LayoutParams
        when (item.role) {
            AgentChatMessage.Role.USER -> {
                holder.binding.messageText.text = item.text
                holder.binding.thinkingHeader.visibility = View.GONE
                holder.binding.thinkingText.visibility = View.GONE
                layoutParams.gravity = Gravity.END
                holder.binding.messageCard.setCardBackgroundColor(holder.itemView.context.getColor(R.color.colorPrimary))
                holder.binding.messageText.setTextColor(holder.itemView.context.getColor(android.R.color.white))
            }

            AgentChatMessage.Role.ASSISTANT -> {
                if (item.isPlaceholder) {
                    holder.binding.messageText.text = item.text
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.grayA5))
                    holder.binding.thinkingHeader.visibility = View.GONE
                    holder.binding.thinkingText.visibility = View.GONE
                } else {
                    getMarkwon(holder.itemView.context).setMarkdown(holder.binding.messageText, item.text)
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.black))

                    if (item.thinking != null) {
                        holder.binding.thinkingHeader.visibility = View.VISIBLE
                        holder.binding.thinkingHeader.text = if (item.isThinkingExpanded) "▼ 思考过程" else "▶ 思考过程"
                        holder.binding.thinkingHeader.setOnClickListener {
                            toggleThinking(position)
                        }

                        if (item.isThinkingExpanded) {
                            holder.binding.thinkingText.visibility = View.VISIBLE
                            holder.binding.thinkingText.text = item.thinking
                        } else {
                            holder.binding.thinkingText.visibility = View.GONE
                        }
                    } else {
                        holder.binding.thinkingHeader.visibility = View.GONE
                        holder.binding.thinkingText.visibility = View.GONE
                    }
                }
                layoutParams.gravity = Gravity.START
                holder.binding.messageCard.setCardBackgroundColor(holder.itemView.context.getColor(R.color.baseWhite))
            }

            AgentChatMessage.Role.TRACE -> {
            }
        }
        holder.binding.messageCard.layoutParams = layoutParams
    }

    private fun toggleThinking(position: Int) {
        val item = items[position]
        if (item.role == AgentChatMessage.Role.ASSISTANT && item.thinking != null) {
            items[position] = item.copy(isThinkingExpanded = !item.isThinkingExpanded)
            notifyItemChanged(position)
        }
    }

    fun submitList(newItems: List<AgentChatMessage>) {
        items.clear()
        items.addAll(newItems.filter { it.role != AgentChatMessage.Role.TRACE })
        notifyDataSetChanged()
    }
}
