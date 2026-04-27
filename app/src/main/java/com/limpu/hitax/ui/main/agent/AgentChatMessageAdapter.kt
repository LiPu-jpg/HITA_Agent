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
    init {
        LogUtils.d( "========== AgentChatMessageAdapter CREATED ==========")
    }

    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingPosition = 0
    private val thinkingTexts = listOf("正在思考", "正在思考.", "正在思考..", "正在思考...")
    private val thinkingRunnable = object : Runnable {
        override fun run() {
            // 更新所有placeholder消息的状态文本
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
            LogUtils.d( "Creating Markwon instance")
            val builder = Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())

            try {
                builder.usePlugin(JLatexMathPlugin.create(13f))
                LogUtils.d( "JLatexMathPlugin enabled")
            } catch (e: Exception) {
                LogUtils.e( "Failed to enable JLatexMathPlugin", e)
            }

            builder.build().also {
                markwon = it
                LogUtils.d( "Markwon instance created successfully")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        LogUtils.d( "=== onCreateViewHolder called ===")
        LogUtils.d( "ViewType: $viewType")
        LogUtils.d( "Parent context: ${parent.context.javaClass.simpleName}")

        val binding = ItemAgentChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        LogUtils.d( "Binding created successfully")
        LogUtils.d( "Card view ID: ${binding.messageCard.id}")
        LogUtils.d( "Card view class: ${binding.messageCard.javaClass.simpleName}")

        return MessageHolder(binding)
    }

    override fun getItemCount(): Int {
        val count = items.size
        LogUtils.d( "getItemCount called: $count items")
        return count
    }

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val item = items[position]
        LogUtils.d( "=== onBindViewHolder called ===")
        LogUtils.d( "Position: $position")
        LogUtils.d( "Message role: ${item.role}")
        LogUtils.d( "Message text: ${item.text.take(50)}...")
        LogUtils.d( "Is placeholder: ${item.isPlaceholder}")

        // 首先重置所有颜色到默认状态
        LogUtils.d( "Step 1: Resetting colors to default")
        holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
        holder.binding.messageCard.strokeWidth = 0
        holder.binding.messageText.setTextColor(Color.BLACK)
        LogUtils.d( "Step 1 complete: Reset to WHITE")

        val layoutParams = holder.binding.messageCard.layoutParams as FrameLayout.LayoutParams
        when (item.role) {
            AgentChatMessage.Role.USER -> {
                LogUtils.d( "=== Processing USER message ===")
                holder.binding.messageText.text = item.text
                holder.binding.thinkingIndicator.visibility = View.GONE
                holder.binding.thinkingHeader.visibility = View.GONE
                holder.binding.thinkingText.visibility = View.GONE
                layoutParams.gravity = Gravity.END

                // 用户消息使用蓝色气泡 (多重设置确保生效)
                val blueColor = Color.parseColor("#304ffe")
                LogUtils.d( "Target blue color: #0x${Integer.toHexString(blueColor)}")

                LogUtils.d( "Step 2: Setting card background to blue")
                holder.binding.messageCard.setCardBackgroundColor(blueColor)

                LogUtils.d( "Step 3: Setting view background to blue")
                holder.binding.messageCard.setBackgroundColor(blueColor)

                LogUtils.d( "Step 4: Setting stroke width to 0")
                holder.binding.messageCard.strokeWidth = 0

                LogUtils.d( "Step 5: Setting text color to white")
                holder.binding.messageText.setTextColor(Color.WHITE)

                // 验证设置结果
                LogUtils.d( "Card view class: ${holder.binding.messageCard.javaClass.simpleName}")
                LogUtils.d( "Card background color AFTER setting: #0x${Integer.toHexString(holder.binding.messageCard.cardBackgroundColor.defaultColor)}")

                LogUtils.d( "=== USER message processing complete ===")
            }

            AgentChatMessage.Role.ASSISTANT -> {
                LogUtils.d( "=== Processing ASSISTANT message ===")
                LogUtils.d( "Is placeholder: ${item.isPlaceholder}")

                if (item.isPlaceholder) {
                    holder.binding.messageText.text = item.text
                    holder.binding.messageText.setTextColor(holder.itemView.context.getColor(R.color.grayA5))
                    holder.binding.thinkingIndicator.visibility = View.VISIBLE
                    holder.binding.thinkingStatusText.text = thinkingTexts[thinkingPosition]
                    holder.binding.thinkingHeader.visibility = View.GONE
                    holder.binding.thinkingText.visibility = View.GONE
                } else {
                    holder.binding.thinkingIndicator.visibility = View.GONE
                    // Use Markwon for all markdown + LaTeX rendering
                    LogUtils.d( "Rendering markdown message: ${item.text.take(50)}...")
                    getMarkwon(holder.itemView.context).setMarkdown(holder.binding.messageText, item.text)
                    // Enable link clicking
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
                // AI助手消息使用白色气泡
                LogUtils.d( "Step 6: Setting ASSISTANT card to WHITE")
                holder.binding.messageCard.setCardBackgroundColor(Color.WHITE)
                holder.binding.messageCard.setBackgroundColor(Color.WHITE)
                holder.binding.messageCard.strokeWidth = 0
                LogUtils.d( "=== ASSISTANT message processing complete ===")
            }

            AgentChatMessage.Role.TRACE -> {
            }
        }
        holder.binding.messageCard.layoutParams = layoutParams

        // 最终状态验证
        LogUtils.d( "=== Final state for position $position ===")
        LogUtils.d( "Role: ${item.role}")
        LogUtils.d( "Gravity: ${if (layoutParams.gravity == Gravity.END) "RIGHT (USER)" else "LEFT (ASSISTANT)"}")
        LogUtils.d( "Card background color: #0x${Integer.toHexString(holder.binding.messageCard.cardBackgroundColor.defaultColor)}")
        LogUtils.d( "Stroke width: ${holder.binding.messageCard.strokeWidth}")
        LogUtils.d( "========================================")
    }

    private fun toggleThinking(position: Int) {
        val item = items[position]
        if (item.role == AgentChatMessage.Role.ASSISTANT && item.thinking != null) {
            items[position] = item.copy(isThinkingExpanded = !item.isThinkingExpanded)
            notifyItemChanged(position)
        }
    }

    fun submitList(newItems: List<AgentChatMessage>) {
        LogUtils.d( "=== submitList called ===")
        LogUtils.d( "New items count: ${newItems.size}")
        LogUtils.d( "Items breakdown:")
        newItems.forEachIndexed { index, msg ->
            LogUtils.d( "  [$index] role=${msg.role}, text='${msg.text.take(30)}...', isPlaceholder=${msg.isPlaceholder}")
        }

        items.clear()
        val filtered = newItems.filter { it.role != AgentChatMessage.Role.TRACE }
        items.addAll(filtered)
        LogUtils.d( "After filtering TRACE: ${filtered.size} items")

        // 强制打印所有消息的详细信息
        items.forEachIndexed { index, msg ->
            LogUtils.d( "Item[$index]: role=${msg.role}, hasText=${msg.text.isNotEmpty()}, len=${msg.text.length}")
        }

        notifyDataSetChanged()
        LogUtils.d( "notifyDataSetChanged called, total items: ${itemCount}")

        // 验证是否真的设置成功
        LogUtils.d( "Verification: items.size=${items.size}, itemCount=${itemCount}")
    }
}
