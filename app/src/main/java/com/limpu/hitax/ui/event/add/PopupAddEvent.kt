package com.limpu.hitax.ui.event.add

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.recyclerview.widget.LinearLayoutManager
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentSession
import com.limpu.hitax.agent.timetable.ArrangementInput
import com.limpu.hitax.agent.timetable.TimetableAgentFactory
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.TeacherInfoRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.databinding.DialogBottomAddEventBinding
import com.limpu.hitax.ui.subject.SubjectAgentTraceListAdapter
import com.limpu.hitax.ui.widgets.PopUpCalendarPicker
import com.limpu.hitax.ui.widgets.PopUpPickCourseTime
import com.limpu.hitax.ui.widgets.PopUpTimePeriodPicker
import com.limpu.style.widgets.DialogAutoEditText
import com.limpu.style.widgets.DialogSelectableLiveList
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog
import java.util.Calendar

class PopupAddEvent(private val addSubjectMode: Boolean = false) :
    TransparentModeledBottomSheetDialog<AddEventViewModel, DialogBottomAddEventBinding>() {

    var initTimetable: Timetable? = null
    var initSubject: TermSubject? = null
    var initCourseTime: CourseTime? = null

    override fun getViewModelClass(): Class<AddEventViewModel> {
        return AddEventViewModel::class.java
    }

    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_add_event
    }

    override fun initViewBinding(v: View): DialogBottomAddEventBinding {
        return DialogBottomAddEventBinding.bind(v)
    }

    private val timetableAgentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput> by lazy {
        TimetableAgentFactory.createProvider()
    }
    private var timetableAgentSession: AgentSession<TimetableAgentInput, TimetableAgentOutput>? = null
    private var submittingByAgent = false
    private lateinit var agentTraceAdapter: SubjectAgentTraceListAdapter

    fun setInitTimetable(timetable: Timetable?): PopupAddEvent {
        initTimetable = timetable
        return this
    }

    fun setInitTime(dow: Int, week: Int, period: TimePeriodInDay): PopupAddEvent {
        val ct = CourseTime()
        ct.dow = dow
        ct.weeks = mutableListOf(week)
        ct.period = period
        initCourseTime = ct
        return this
    }

    fun setInitSubject(subject: TermSubject): PopupAddEvent {
        initSubject = subject
        return this
    }

    @SuppressLint("SetTextI18n")
    override fun initViews(view: View) {
        binding?.title?.setText(if (addSubjectMode) R.string.add_subject else R.string.ade_title)
        binding?.cancel?.setOnClickListener { dismiss() }

        binding?.modeBatch?.setOnClickListener {
            viewModel.setAddMode(AddEventViewModel.AddMode.BATCH_PERIOD)
        }
        binding?.modeFree?.setOnClickListener {
            viewModel.setAddMode(AddEventViewModel.AddMode.FREE_RANGE)
        }

        initAgentTraceList()

        viewModel.doneLiveData.observe(this) {
            if (it) binding?.adeBtDone?.show() else binding?.adeBtDone?.hide()
        }

        viewModel.addModeLiveData.observe(this) { mode ->
            applyModeUi(mode)
            refreshSubjectVisibility(viewModel.subjectLiveData.value)
            refreshTeacherVisibility(viewModel.teacherLiveData.value)
            refreshTimeTextByMode(mode)
        }

        viewModel.customDateLiveData.observe(this) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                binding?.pickDate?.setCardBackgroundColor(getColorPrimary())
                binding?.pickDateIcon?.setColorFilter(getColorPrimary())
                binding?.dateShow?.setTextColor(getColorPrimary())
                binding?.dateShow?.text = formatDateLabel(state.data ?: 0L)
            } else {
                binding?.pickDate?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickDateIcon?.clearColorFilter()
                binding?.dateShow?.setTextColor(getTextColorSecondary())
                binding?.dateShow?.text = getString(R.string.ade_set_date)
            }
        }

        viewModel.customTimePeriodLiveData.observe(this) {
            if (viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE) {
                if (it.state == DataState.STATE.SUCCESS) {
                    binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                    binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                    binding?.timeShow?.setTextColor(getColorPrimary())
                    binding?.timeShow?.text = formatTimePeriodLabel(it.data)
                } else {
                    binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                    binding?.pickTimeIcon?.clearColorFilter()
                    binding?.timeShow?.setTextColor(getTextColorSecondary())
                    binding?.timeShow?.text = getString(R.string.ade_pick_time_range)
                }
            }
        }

        viewModel.teacherLiveData.observe(this) {
            refreshTeacherVisibility(it)
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTeacherIcon?.setColorFilter(getColorPrimary())
                binding?.pickTeacherText?.setTextColor(getColorPrimary())
                binding?.pickTeacher?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTeacherText?.text = it.data
                binding?.pickTeacherCancel?.visibility = if (viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE) View.GONE else View.VISIBLE
            } else {
                binding?.pickTeacherText?.text = getString(R.string.ade_pick_teacher)
                binding?.pickTeacher?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTeacherText?.setTextColor(getTextColorSecondary())
                binding?.pickTeacherCancel?.visibility = View.GONE
                binding?.pickTeacherIcon?.clearColorFilter()
            }
        }

        viewModel.locationLiveData.observe(this) {
            binding?.pickLocation?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickLocationIcon?.setColorFilter(getColorPrimary())
                binding?.pickLocationText?.setTextColor(getColorPrimary())
                binding?.pickLocation?.setCardBackgroundColor(getColorPrimary())
                binding?.pickLocationText?.text = it.data
                binding?.pickLocationCancel?.visibility = View.VISIBLE
            } else {
                binding?.pickLocationText?.text = getString(R.string.ade_pick_location)
                binding?.pickLocation?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickLocationText?.setTextColor(getTextColorSecondary())
                binding?.pickLocationIcon?.clearColorFilter()
                binding?.pickLocationCancel?.visibility = View.GONE
            }
        }

        viewModel.subjectLiveData.observe(this) {
            refreshSubjectVisibility(it)
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickSubjectIcon?.setColorFilter(getColorPrimary())
                binding?.pickSubjectText?.setTextColor(getColorPrimary())
                binding?.pickSubject?.setCardBackgroundColor(getColorPrimary())
                binding?.pickSubjectText?.text = it.data?.name
                binding?.name?.setText(it.data?.name)
            } else {
                binding?.pickSubjectText?.text = getString(R.string.ade_pick_subject)
                binding?.pickSubject?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickSubjectText?.setTextColor(getTextColorSecondary())
                binding?.pickSubjectIcon?.clearColorFilter()
            }
        }

        viewModel.timetableLiveData.observe(this) {
            binding?.pickTimetable?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTimetableIcon?.setColorFilter(getColorPrimary())
                binding?.pickTimetableText?.setTextColor(getColorPrimary())
                binding?.pickTimetable?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimetableText?.text = it.data?.name
            } else {
                binding?.pickTimetableText?.text = getString(R.string.ade_pick_timetable)
                binding?.pickTimetable?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTimetableText?.setTextColor(getTextColorSecondary())
                binding?.pickTimetableIcon?.clearColorFilter()
            }
        }

        viewModel.timeRangeLiveDate.observe(this) {
            if (viewModel.addModeLiveData.value != AddEventViewModel.AddMode.BATCH_PERIOD) return@observe
            binding?.pickTime?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                binding?.timeShow?.setTextColor(getColorPrimary())
                if (viewModel.timetableLiveData.value?.data != null) {
                    it.data?.let { ct ->
                        val t1 = resources.getStringArray(R.array.dow1)[ct.dow - 1].toString() +
                                " " + ct.period.from.toString() + "-" + ct.period.to.toString()
                        val set = HashSet<Int>()
                        val frags = mutableListOf<String>()
                        for (i in ct.weeks) {
                            set.add(i)
                        }
                        for (s in set) {
                            if (set.contains(s - 1)) continue
                            var ts = s
                            while (set.contains(ts + 1)) {
                                ts++
                            }
                            when (ts) {
                                s -> frags.add("$s")
                                s + 1 -> {
                                    frags.add("$s")
                                    frags.add("$ts")
                                }
                                else -> frags.add("$s-$ts")
                            }
                        }
                        binding?.timeShow?.text = "${frags.joinToString(", ")}周 $t1"
                    }
                }
            } else {
                binding?.timeShow?.text = getString(R.string.ade_pick_time)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.timeShow?.setTextColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
            }
        }

        binding?.pickTimetable?.setOnClickListener {
            DialogSelectableLiveList<Timetable>().setTitle(R.string.ade_pick_timetable)
                .setInitValue(viewModel.timetableLiveData.value?.data)
                .setDataLoader(object : DialogSelectableLiveList.DataLoader<Timetable> {
                    override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<Timetable>>> {
                        return TimetableRepository.getInstance(activity!!.application).getTimetables()
                            .switchMap {
                                val res = mutableListOf<DialogSelectableLiveList.ItemData<Timetable>>()
                                for (data: Timetable in it) {
                                    res.add(DialogSelectableLiveList.ItemData(data.name, data))
                                }
                                MutableLiveData(res)
                            }
                    }
                }).setOnConfirmListener(object :
                    DialogSelectableLiveList.OnConfirmListener<Timetable> {
                    override fun onConfirm(title: String?, key: Timetable) {
                        viewModel.timetableLiveData.value = DataState(key)
                    }
                }).show(childFragmentManager, "set_timetable")
        }

        binding?.pickSubject?.setOnClickListener {
            DialogSelectableLiveList<TermSubject>().setTitle(R.string.ade_pick_subject)
                .setInitValue(viewModel.subjectLiveData.value?.data)
                .setDataLoader(object : DialogSelectableLiveList.DataLoader<TermSubject> {
                    override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<TermSubject>>> {
                        return SubjectRepository.getInstance(activity!!.application)
                            .getSubjects(viewModel.timetableLiveData.value?.data?.id ?: "")
                            .switchMap {
                                val res = mutableListOf<DialogSelectableLiveList.ItemData<TermSubject>>()
                                for (data: TermSubject in it) {
                                    res.add(DialogSelectableLiveList.ItemData(data.name, data))
                                }
                                MutableLiveData(res)
                            }
                    }
                }).setOnConfirmListener(object :
                    DialogSelectableLiveList.OnConfirmListener<TermSubject> {
                    override fun onConfirm(title: String?, key: TermSubject) {
                        viewModel.subjectLiveData.value = DataState(key)
                    }
                }).show(childFragmentManager, "set_subject")
        }

        binding?.pickDate?.setOnClickListener {
            val initDate = viewModel.customDateLiveData.value?.data
            PopUpCalendarPicker()
                .setInitValue(initDate)
                .setOnConfirmListener(object : PopUpCalendarPicker.OnConfirmListener {
                    override fun onConfirm(c: Calendar) {
                        c.set(Calendar.HOUR_OF_DAY, 0)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        viewModel.setCustomDate(c.timeInMillis)
                    }
                })
                .show(childFragmentManager, "pick_custom_date")
        }

        binding?.pickTime?.setOnClickListener {
            when (viewModel.addModeLiveData.value ?: AddEventViewModel.AddMode.BATCH_PERIOD) {
                AddEventViewModel.AddMode.BATCH_PERIOD -> {
                    viewModel.timetableLiveData.value?.data?.let { tt ->
                        PopUpPickCourseTime(tt)
                            .setInitialValue(tt, viewModel.timeRangeLiveDate.value?.data)
                            .setSelectListener(object : PopUpPickCourseTime.OnTimeSelectedListener {
                                override fun onSelected(data: CourseTime) {
                                    if (data.weeks.isEmpty()) {
                                        viewModel.timeRangeLiveDate.value = DataState(DataState.STATE.NOTHING)
                                    } else {
                                        viewModel.timeRangeLiveDate.value = DataState(data)
                                    }
                                }
                            }).show(childFragmentManager, "pick_course_time")
                    }
                }

                AddEventViewModel.AddMode.FREE_RANGE -> {
                    val initPeriod = viewModel.customTimePeriodLiveData.value?.data
                    PopUpTimePeriodPicker()
                        .setDialogTitle(R.string.ade_pick_time_range)
                        .setInitialValue(initPeriod?.from, initPeriod?.to)
                        .setOnDialogConformListener(object : PopUpTimePeriodPicker.OnDialogConformListener {
                            override fun onClick(timePeriodInDay: TimePeriodInDay) {
                                viewModel.setCustomTimePeriod(
                                    TimeInDay(timePeriodInDay.from.hour, timePeriodInDay.from.minute),
                                    TimeInDay(timePeriodInDay.to.hour, timePeriodInDay.to.minute)
                                )
                            }
                        }).show(childFragmentManager, "pick_custom_time_range")
                }
            }
        }

        binding?.pickTeacherCancel?.setOnClickListener {
            viewModel.teacherLiveData.value = DataState(DataState.STATE.NOTHING)
        }
        binding?.pickTeacher?.setOnClickListener {
            DialogAutoEditText().setTitle(getString(R.string.ade_pick_teacher))
                .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                    override fun OnConfirm(content: String) {
                        viewModel.teacherLiveData.value = DataState(content)
                    }
                }).setInitValue(viewModel.teacherLiveData.value?.data ?: "")
                .setDataLoader(object : DialogAutoEditText.DataLoader {
                    override fun loadData(str: String): LiveData<List<String>> {
                        return TeacherInfoRepository.getInstance(activity!!.application)
                            .searchTeachers(str).switchMap {
                                val r = mutableListOf<String>()
                                it.data?.let { dt ->
                                    for (t in dt) {
                                        r.add(t.name)
                                    }
                                }
                                MutableLiveData(r)
                            }
                    }
                }).show(childFragmentManager, "pick_teacher")
        }

        binding?.pickLocationCancel?.setOnClickListener {
            viewModel.locationLiveData.value = DataState(DataState.STATE.NOTHING)
        }
        binding?.pickLocation?.setOnClickListener {
            DialogAutoEditText().setTitle(getString(R.string.ade_pick_location))
                .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                    override fun OnConfirm(content: String) {
                        viewModel.locationLiveData.value = DataState(content)
                    }
                }).setInitValue(viewModel.locationLiveData.value?.data ?: "")
                .setDataLoader(object : DialogAutoEditText.DataLoader {
                    override fun loadData(str: String): LiveData<List<String>> {
                        return TimetableRepository.getInstance(activity!!.application)
                            .searchLocation(str)
                    }
                }).show(childFragmentManager, "pick_location")
        }

        binding?.adeBtDone?.setOnClickListener {
            when (viewModel.addModeLiveData.value ?: AddEventViewModel.AddMode.BATCH_PERIOD) {
                AddEventViewModel.AddMode.BATCH_PERIOD -> {
                    viewModel.createEvent()
                    dismiss()
                }

                AddEventViewModel.AddMode.FREE_RANGE -> {
                    submitFreeRangeByAgent()
                }
            }
        }
        binding?.name?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                viewModel.nameLiveData.value = p0.toString()
            }
        })

        viewModel.init(addSubjectMode, initTimetable, initSubject, initCourseTime)
    }

    private fun initAgentTraceList() {
        agentTraceAdapter = SubjectAgentTraceListAdapter()
        binding?.agentTraceList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.agentTraceList?.adapter = agentTraceAdapter
        binding?.agentTraceContainer?.visibility = View.GONE
    }

    private fun updateAgentTraceUiByMode(mode: AddEventViewModel.AddMode) {
        if (mode == AddEventViewModel.AddMode.FREE_RANGE) {
            if (submittingByAgent || agentTraceAdapter.itemCount > 0) {
                binding?.agentTraceContainer?.visibility = View.VISIBLE
            }
        } else {
            binding?.agentTraceContainer?.visibility = View.GONE
        }
    }

    private fun showAgentTraceStatus(text: String) {
        binding?.agentTraceStatus?.text = text
    }

    private fun applyModeUi(mode: AddEventViewModel.AddMode) {
        val isBatch = mode == AddEventViewModel.AddMode.BATCH_PERIOD
        binding?.pickDate?.visibility = if (isBatch) View.GONE else View.VISIBLE
        if (isBatch) {
            binding?.pickTime?.visibility =
                if (viewModel.timeRangeLiveDate.value?.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
        } else {
            binding?.pickTime?.visibility = View.VISIBLE
        }
        binding?.modeBatch?.setCardBackgroundColor(if (isBatch) getColorPrimary() else getTextColorSecondary())
        binding?.modeBatchText?.setTextColor(if (isBatch) getColorPrimary() else getTextColorSecondary())
        binding?.modeFree?.setCardBackgroundColor(if (isBatch) getTextColorSecondary() else getColorPrimary())
        binding?.modeFreeText?.setTextColor(if (isBatch) getTextColorSecondary() else getColorPrimary())
        updateAgentTraceUiByMode(mode)
    }

    private fun refreshTeacherVisibility(state: DataState<String>?) {
        val isFreeMode = viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE
        val hiddenByState = state?.state == DataState.STATE.FETCH_FAILED
        binding?.pickTeacher?.visibility = if (isFreeMode || hiddenByState) View.GONE else View.VISIBLE
        if (isFreeMode) {
            binding?.pickTeacherCancel?.visibility = View.GONE
        }
    }

    private fun refreshSubjectVisibility(state: DataState<TermSubject>?) {
        val isFreeMode = viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE
        val hiddenByState = state?.state == DataState.STATE.FETCH_FAILED || state?.state == DataState.STATE.SPECIAL
        binding?.pickSubject?.visibility = if (isFreeMode || hiddenByState) View.GONE else View.VISIBLE
    }

    private fun refreshTimeTextByMode(mode: AddEventViewModel.AddMode) {
        if (mode == AddEventViewModel.AddMode.BATCH_PERIOD) {
            if (viewModel.timeRangeLiveDate.value?.state != DataState.STATE.SUCCESS) {
                binding?.timeShow?.text = getString(R.string.ade_pick_time)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.timeShow?.setTextColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
            }
        } else {
            val periodState = viewModel.customTimePeriodLiveData.value
            if (periodState?.state == DataState.STATE.SUCCESS) {
                binding?.timeShow?.text = formatTimePeriodLabel(periodState.data)
                binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                binding?.timeShow?.setTextColor(getColorPrimary())
            } else {
                binding?.timeShow?.text = getString(R.string.ade_pick_time_range)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
                binding?.timeShow?.setTextColor(getTextColorSecondary())
            }
        }
    }

    private fun submitFreeRangeByAgent() {
        if (submittingByAgent) return
        val fromTo = viewModel.customFromToLiveData.value?.data ?: return
        val eventName = (viewModel.nameLiveData.value ?: "").trim()
        if (eventName.isBlank()) return

        val timetableId = viewModel.timetableLiveData.value?.data?.id
        val place = if (viewModel.locationLiveData.value?.state == DataState.STATE.SUCCESS) {
            viewModel.locationLiveData.value?.data.orEmpty()
        } else {
            ""
        }

        val session = timetableAgentProvider.createSession()
        timetableAgentSession?.dispose()
        timetableAgentSession = session
        submittingByAgent = true
        binding?.adeBtDone?.isEnabled = false
        agentTraceAdapter.clear()
        updateAgentTraceUiByMode(AddEventViewModel.AddMode.FREE_RANGE)
        showAgentTraceStatus(getString(R.string.loading))

        session.run(
            input = TimetableAgentInput(
                application = requireActivity().application,
                action = TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT,
                timetableId = timetableId,
                arrangement = ArrangementInput(
                    name = eventName,
                    fromMs = fromTo.first,
                    toMs = fromTo.second,
                    place = place,
                ),
            ),
            onTrace = { trace ->
                activity?.runOnUiThread {
                    binding?.agentTraceContainer?.visibility = View.VISIBLE
                    agentTraceAdapter.append(trace)
                    if (agentTraceAdapter.itemCount > 0) {
                        binding?.agentTraceList?.scrollToPosition(agentTraceAdapter.itemCount - 1)
                    }
                }
            },
            onResult = { result ->
                activity?.runOnUiThread {
                    submittingByAgent = false
                    binding?.adeBtDone?.isEnabled = true
                    if (result.ok) {
                        dismiss()
                    } else {
                        showAgentTraceStatus(result.error ?: getString(R.string.operation_failed))
                        Toast.makeText(
                            requireContext(),
                            result.error ?: getString(R.string.operation_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        timetableAgentSession?.dispose()
        timetableAgentSession = null
        super.onDestroy()
    }

    private fun formatDateLabel(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    private fun formatTimePeriodLabel(period: TimePeriodInDay?): String {
        if (period == null) return getString(R.string.ade_pick_time_range)
        return "${period.from}-${period.to}"
    }
}
