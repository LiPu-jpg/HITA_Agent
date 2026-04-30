package com.limpu.hitax.ui.main.agent

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ItemAgentChatMessageBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import android.os.Handler
import android.os.Looper
import com.limpu.hitax.utils.LogUtils

class AgentChatMessageAdapter : RecyclerView.Adapter<AgentChatMessageAdapter.MessageHolder>() {
    private val items = mutableListOf<AgentChatMessage>()
    private var markwon: Markwon? = null

    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingPosition = 0
    private val thinkingTexts = listOf("正在思考", "正在思考.", "正在思考..", "正在思考...")
    private val thinkingRunnable = object : Runnable {
        override fun run() {
            items.forEachIndexed { index, message ->
                if (message.role == AgentChatMessage.Role.ASSISTANT && message.isPlaceholder) {
                    notifyItemChanged(index)
                }
            }
            thinkingPosition = (thinkingPosition + 1) % thinkingTexts.size
            thinkingHandler.postDelayed(this, 500)
        }
    }

    init {
        startThinkingAnimation()
    }

    private fun startThinkingAnimation() {
        thinkingHandler.postDelayed(thinkingRunnable, 500)
    }

    private fun stopThinkingAnimation() {
        thinkingHandler.removeCallbacks(thinkingRunnable)
    }

    class MessageHolder(val binding: ItemAgentChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    private fun getMarkwon(context: Context): Markwon {
        return markwon ?: run {
            val builder = Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())

            try {
                builder.usePlugin(JLatexMathPlugin.create(13f))
            } catch (e: Exception) {
                LogUtils.e("Failed to enable JLatexMathPlugin", e)
            }

            builder.build().also { markwon = it }
        }
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

        holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
        holder.binding.messageCard.strokeWidth = 0
        holder.binding.messageText.setTextColor(Color.BLACK)

        val layoutParams = holder.binding.messageCard.layoutParams as FrameLayout.LayoutParams
        when (item.role) {
            AgentChatMessage.Role.USER -> {
                holder.binding.messageText.text = item.text
                holder.binding.thinkingIndicator.visibility = View.GONE
                holder.binding.thinkingHeader.visibility = View.GONE
                holder.binding.thinkingText.visibility = View.GONE
                layoutParams.gravity = Gravity.END

                val blueColor = Color.parseColor("#304ffe")
                holder.binding.messageCard.setCardBackgroundColor(blueColor)
                holder.binding.messageCard.strokeWidth = 0
                holder.binding.messageText.setTextColor(Color.WHITE)
            }

            AgentChatMessage.Role.ASSISTANT -> {
                if (item.isPlaceholder) {
                    holder.binding.messageText.text = item.text
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.grayA5))
                    holder.binding.thinkingIndicator.visibility = View.VISIBLE
                    holder.binding.thinkingStatusText.text = thinkingTexts[thinkingPosition]
                    holder.binding.thinkingHeader.visibility = View.GONE
                    if (item.thinking.isNullOrBlank()) {
                        holder.binding.thinkingText.visibility = View.GONE
                    } else {
                        holder.binding.thinkingText.visibility = View.VISIBLE
                        holder.binding.thinkingText.text = item.thinking
                    }
                } else {
                    holder.binding.thinkingIndicator.visibility = View.GONE
                    getMarkwon(holder.itemView.context).setMarkdown(holder.binding.messageText, item.text)
                    holder.binding.messageText.movementMethod = LinkMovementMethod.getInstance()
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
                holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
                holder.binding.messageCard.strokeWidth = 0
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
        val filtered = newItems.filter { it.role != AgentChatMessage.Role.TRACE }
        items.addAll(filtered)
        notifyDataSetChanged()
    }
}
