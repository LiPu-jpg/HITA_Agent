package com.limpu.hitax.ui.main.agent

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import com.limpu.hitax.R
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentSession
import com.limpu.hitax.agent.timetable.ArrangementInput
import com.limpu.hitax.agent.timetable.TimetableAgentFactory
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.databinding.DialogAgentChatBinding
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class AgentChatDialog :
    TransparentModeledBottomSheetDialog<AgentChatViewModel, DialogAgentChatBinding>() {

    override fun getViewModelClass(): Class<AgentChatViewModel> =
        AgentChatViewModel::class.java

    override fun getLayoutId(): Int = R.layout.dialog_agent_chat

    override fun initViewBinding(v: View): DialogAgentChatBinding =
        DialogAgentChatBinding.bind(v)

    private val agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput> by lazy {
        TimetableAgentFactory.createProvider()
    }
    private var agentSession: AgentSession<TimetableAgentInput, TimetableAgentOutput>? = null

    private lateinit var messageAdapter: AgentChatMessageAdapter

    override fun initViews(view: View) {
        messageAdapter = AgentChatMessageAdapter()
        binding?.messageList?.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = messageAdapter
        }

        binding?.closeButton?.setOnClickListener { dismiss() }
        binding?.sendButton?.setOnClickListener { sendMessage() }

        binding?.inputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        viewModel.messages.observe(this) { list ->
            messageAdapter.submitList(list)
            if (list.isNotEmpty()) {
                binding?.messageList?.scrollToPosition(list.size - 1)
            }
        }
        viewModel.status.observe(this) { text ->
            if (text.isNotEmpty()) {
                binding?.statusText?.text = text
            }
        }
        viewModel.isLoading.observe(this) { loading ->
            binding?.sendButton?.isEnabled = !loading
            binding?.inputField?.isEnabled = !loading
        }
    }

    private fun sendMessage() {
        val text = binding?.inputField?.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        if (viewModel.isLoading.value == true) return

        binding?.inputField?.text?.clear()

        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = text))

        viewModel.sendToLlm(text, agentProvider)
    }

    private fun detectAction(text: String): TimetableAgentInput.Action {
        val queryKeywords = listOf("查询", "课表", "今天", "明天", "课程", "安排", "日程", "有什么")
        return if (queryKeywords.any { text.contains(it) }) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE
        } else {
            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT
        }
    }

    private fun buildAgentInput(action: TimetableAgentInput.Action, text: String): TimetableAgentInput {
        return when (action) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> {
                val cal = Calendar.getInstance()
                val startOfDay = cal.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)
                val endOfDay = startOfDay.clone() as Calendar
                endOfDay.add(Calendar.DAY_OF_YEAR, 1)

                TimetableAgentInput(
                    application = requireActivity().application,
                    action = action,
                    fromMs = startOfDay.timeInMillis,
                    toMs = endOfDay.timeInMillis,
                )
            }

            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> {
                val now = Calendar.getInstance()
                val fromMs = now.timeInMillis
                now.add(Calendar.HOUR_OF_DAY, 1)

                TimetableAgentInput(
                    application = requireActivity().application,
                    action = action,
                    arrangement = ArrangementInput(
                        name = text,
                        fromMs = fromMs,
                        toMs = now.timeInMillis,
                    ),
                )
            }
        }
    }

    private fun formatResult(output: TimetableAgentOutput): String {
        return when (output.action) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> {
                if (output.events.isEmpty()) {
                    "当前没有找到课程安排。"
                } else {
                    buildString {
                        append("找到 ${output.events.size} 个课程安排:\n")
                        output.events.take(10).forEach { ev ->
                            append("\n• ${ev.name}")
                            if (ev.place.isNotBlank()) append("  @ ${ev.place}")
                        }
                        if (output.events.size > 10) {
                            append("\n\n…还有 ${output.events.size - 10} 个")
                        }
                    }
                }
            }

            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> {
                if (output.addedEventIds.isNotEmpty()) {
                    "已成功添加 ${output.addedEventIds.size} 个活动。"
                } else {
                    "活动添加成功。"
                }
            }
        }
    }

    override fun onDestroy() {
        agentSession?.dispose()
        agentSession = null
        super.onDestroy()
    }
}
