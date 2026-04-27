package com.limpu.hitax.ui.eas.imp

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.source.preference.EasPreferenceSource
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import com.limpu.hitax.databinding.ActivityEasImportBinding
import androidx.activity.viewModels
import com.limpu.hitax.ui.eas.EASActivity
import com.limpu.hitax.ui.widgets.PopUpCalendarPicker
import dagger.hilt.android.AndroidEntryPoint
import com.limpu.hitax.ui.widgets.PopUpTimePeriodPicker
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.AnimationUtils
import com.limpu.hitax.utils.ImageUtils.dp2px
import com.limpu.hitax.utils.TermNameFormatter
import com.limpu.hitax.utils.TextTools
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.widgets.PopUpCheckableList

@AndroidEntryPoint
class ImportTimetableActivity :
    EASActivity<ImportTimetableViewModel, ActivityEasImportBinding>() {

    override val viewModel: ImportTimetableViewModel by viewModels()

    override fun shouldRefreshOnStart(): Boolean = false

    override fun shouldCheckLoginOnStart(): Boolean = false

    private lateinit var scheduleStructureAdapter: TimetableStructureListAdapter
    private var autoImportPending: Boolean = false
    private var autoImportTriggered: Boolean = false
    private var importActionInFlight: Boolean = false
    private var termQueryInFlight: Boolean = false
    private var calibrationPromptTermCode: String? = null
    private var openTermPickerWhenLoaded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        initList()
        autoImportPending = intent.getBooleanExtra("autoImport", false)
        binding.toolbar.title = ""
        binding.collapse.title = ""
        binding.appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val scale = 1.0f + verticalOffset / appBarLayout.height.toFloat()
            binding.termPick.translationX =
                (binding.toolbar.contentInsetStartWithNavigation + dp2px(
                    getThis(),
                    8f
                )) * (1 - scale)
            binding.termPick.scaleX = 0.5f * (1 + scale)
            binding.termPick.scaleY = 0.5f * (1 + scale)
            binding.termPick.translationY =
                (binding.termPick.height / 2) * (1 - binding.termPick.scaleY)

            binding.buttonImport.translationY = dp2px(getThis(), 24f) * (1 - scale)
            binding.buttonImport.scaleX = 0.7f + 0.3f * scale
            binding.buttonImport.scaleY = 0.7f + 0.3f * scale
            binding.buttonImport.translationX =
                (binding.buttonImport.width / 2) * (1 - binding.buttonImport.scaleX)
        })
        binding.cardName.isEnabled = false
        val token = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext)).getEasToken()
        val isUndergrad = token.stutype == EASToken.TYPE.UNDERGRAD
        binding.stutype.isChecked = isUndergrad
        viewModel.changeIsUndergraduate(isUndergrad)
        binding.termPick.setOnClickListener {
            val terms = viewModel.startGetAllTerms()
            if (terms.isEmpty()) {
                openTermPickerWhenLoaded = true
                ensureLoggedInForImport {
                    refresh()
                }
                return@setOnClickListener
            }
            showTermPicker(terms)
        }
        binding.buttonImport.setOnClickListener { button ->
            ensureLoggedInForImport {
                if (viewModel.isBenbuTerm() && viewModel.benbuCalibrationConfirmedLiveData.value != true) {
                    showBenbuCalibrationPrompt(force = true)
                    return@ensureLoggedInForImport
                }
                if (startImportFlow()) {
                    button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
        }
        binding.cardDate.onCardClickListener = View.OnClickListener {
            viewModel.startDateLiveData.value?.data?.let {
                PopUpCalendarPicker().setInitValue(it.timeInMillis)
                    .setOnConfirmListener(object : PopUpCalendarPicker.OnConfirmListener {
                        override fun onConfirm(c: java.util.Calendar) {
                            viewModel.changeStartDate(c)
                        }
                    }).show(supportFragmentManager, "pick")
            }
        }
        binding.buttonPrevWeek.setOnClickListener {
            viewModel.shiftStartDateByWeek(-1)
        }
        binding.buttonNextWeek.setOnClickListener {
            viewModel.shiftStartDateByWeek(1)
        }
        binding.stutype.setOnCheckedChangeListener { bt, b ->
            if (bt.isPressed) {
                viewModel.changeIsUndergraduate(b)
            }
        }

        refreshLocalUiOnly()
        if (EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext)).getEasToken().isLogin()) {
            refresh()
        }
        if (autoImportPending) {
            ensureLoggedInForImport {
                refresh()
            }
        }
    }

    override fun refresh() {
        binding.buttonImport.background = ContextCompat.getDrawable(
            getThis(),
            R.drawable.element_rounded_button_bg_grey
        )
        binding.buttonImport.isEnabled = false
        binding.refresh.isRefreshing = true
        termQueryInFlight = true
        viewModel.startRefreshTerms()
    }

    override fun onLoginCheckSuccess(retry: Boolean) {
        val token = EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext)).getEasToken()
        binding.stutype.isChecked = (token.stutype == EASToken.TYPE.UNDERGRAD)
        viewModel.changeIsUndergraduate(binding.stutype.isChecked)
        refresh()
    }

    private fun refreshLocalUiOnly() {
        binding.buttonImport.background = ContextCompat.getDrawable(
            getThis(),
            R.drawable.element_rounded_button_bg_grey
        )
        binding.buttonImport.isEnabled = false
        binding.refresh.isRefreshing = false
        binding.termText.setText(R.string.pick_import_term)
        binding.cardName.setTitle(getString(R.string.timetable_name))
        binding.cardDate.setTitle(R.string.no_valid_date)
        updateBenbuCalibrationVisibility()
    }

    private fun ensureLoggedInForImport(onSuccess: () -> Unit) {
        if (EASRepository(application, EasPreferenceSource(application.applicationContext), TimetablePreferenceSource(application.applicationContext)).getEasToken().isLogin()) {
            onSuccess()
            return
        }
        ActivityUtils.showEasVerifyWindow<android.app.Activity>(
            from = this,
            directTo = null,
            onResponseListener = object : com.limpu.hitax.ui.eas.login.PopUpLoginEAS.OnResponseListener {
                override fun onSuccess(window: com.limpu.hitax.ui.eas.login.PopUpLoginEAS) {
                    window.dismiss()
                    onSuccess()
                }

                override fun onFailed(window: com.limpu.hitax.ui.eas.login.PopUpLoginEAS) = Unit
            }
        )
    }

    private fun bindLiveData() {
        viewModel.selectedTermLiveData.observe(this) {
            it?.let {
                val label = getDisplayTermName(it)
                binding.termText.text = label
                binding.cardName.setTitle(label)
                updateBenbuCalibrationVisibility()
                maybeAutoImport()
            }
        }
        viewModel.termsLiveData.observe(this) { data ->
            binding.refresh.isRefreshing = false
            when (data.state) {
                DataState.STATE.SUCCESS -> {
                    termQueryInFlight = false
                    resetSessionRetryState()
                    if (!data.data.isNullOrEmpty()) {
                        for (t in data.data!!) {
                            if (t.isCurrent) {
                                viewModel.changeSelectedTerm(t)
                                if (openTermPickerWhenLoaded) {
                                    openTermPickerWhenLoaded = false
                                    showTermPicker(data.data!!)
                                }
                                return@observe
                            }
                        }
                        viewModel.changeSelectedTerm(data.data!![0])
                        if (openTermPickerWhenLoaded) {
                            openTermPickerWhenLoaded = false
                            showTermPicker(data.data!!)
                        }
                    }
                }

                DataState.STATE.NOT_LOGGED_IN -> {
                    if (termQueryInFlight) {
                        if (!handleSessionExpired {
                                termQueryInFlight = true
                                binding.refresh.isRefreshing = true
                                viewModel.startRefreshTerms()
                                true
                            }) {
                            termQueryInFlight = false
                        }
                    }
                }

                DataState.STATE.NOTHING -> Unit

                else -> {
                    termQueryInFlight = false
                    resetSessionRetryState()
                    binding.termText.setText(R.string.load_failed)
                    openTermPickerWhenLoaded = false
                }
            }
            maybeAutoImport()
        }
        viewModel.startDateLiveData.observe(this) {
            if ((it.state == DataState.STATE.SUCCESS || it.state == DataState.STATE.SPECIAL) && it.data != null) {
                binding.cardDate.setTitle(
                    TextTools.getNormalDateText(
                        getThis(),
                        it.data!!
                    )
                )
            } else {
                binding.cardDate.setTitle(R.string.no_valid_date)
            }
            updateBenbuCalibrationVisibility()
            maybeShowBenbuCalibrationPrompt()
            maybeAutoImport()
        }
        viewModel.benbuCalibrationConfirmedLiveData.observe(this) {
            updateBenbuCalibrationVisibility()
            maybeShowBenbuCalibrationPrompt()
            maybeAutoImport()
        }
        viewModel.scheduleStructureLiveData.observe(this) {
            AnimationUtils.enableLoadingButton(binding.buttonImport, !it.data.isNullOrEmpty())
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { data ->
                    scheduleStructureAdapter.notifyItemChangedSmooth(data)
                }
            }
            maybeAutoImport()
        }
        viewModel.isUndergraduateLiveData.observe(this) {
            binding.stutype.text = if (it) getString(R.string.undergrad_structure) else
                getString(R.string.postgrad_structure)
        }
        viewModel.importTimetableResultLiveData.observe(this) {
            AnimationUtils.loadingButtonDone(
                binding.buttonImport, it.state == DataState.STATE.SUCCESS,
                successStr = R.string.import_success, failStr = R.string.import_failed
            )
            if (it.state == DataState.STATE.SUCCESS) {
                importActionInFlight = false
                resetSessionRetryState()
            } else if (it.state == DataState.STATE.NOT_LOGGED_IN && importActionInFlight) {
                if (!handleSessionExpired { retryImportFlow() }) {
                    importActionInFlight = false
                }
            } else if (it.state != DataState.STATE.SUCCESS) {
                importActionInFlight = false
                resetSessionRetryState()
                val msg = it.message?.trim().orEmpty()
                if (msg.isNotEmpty()) {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            WidgetUtils.sendRefreshToAll(this)
        }
    }

    private fun maybeAutoImport() {
        if (!autoImportPending || autoImportTriggered) return
        if (viewModel.isBenbuTerm() && viewModel.benbuCalibrationConfirmedLiveData.value != true) return
        if (startImportFlow()) {
            autoImportTriggered = true
        }
    }

    private fun startImportFlow(): Boolean {
        if (viewModel.startImportTimetable()) {
            importActionInFlight = true
            if (viewModel.isBenbuTerm()) {
                viewModel.saveBenbuCalibration()
            }
            binding.buttonImport.startAnimation()
            return true
        }
        return false
    }

    private fun retryImportFlow(): Boolean {
        if (viewModel.retryImportTimetable()) {
            importActionInFlight = true
            binding.buttonImport.startAnimation()
            return true
        }
        return false
    }

    private fun updateBenbuCalibrationVisibility() {
        val visible = viewModel.isBenbuTerm()
        binding.benbuCalibrationCard.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun maybeShowBenbuCalibrationPrompt() {
        showBenbuCalibrationPrompt(force = false)
    }

    private fun showBenbuCalibrationPrompt(force: Boolean) {
        val term = viewModel.selectedTermLiveData.value ?: return
        val startDate = viewModel.startDateLiveData.value?.data ?: return
        if (!viewModel.isBenbuTerm(term)) return
        if (!force && viewModel.benbuCalibrationConfirmedLiveData.value == true) return
        if (!force && calibrationPromptTermCode == term.getCode()) return
        calibrationPromptTermCode = term.getCode()
        AlertDialog.Builder(this)
            .setTitle(R.string.benbu_start_date_confirm_title)
            .setMessage(
                getString(R.string.benbu_start_date_confirm_message) + "\n\n" +
                    TextTools.getNormalDateText(this, startDate)
            )
            .setPositiveButton(R.string.benbu_start_date_confirm_positive) { _, _ ->
                viewModel.saveBenbuCalibration()
                maybeAutoImport()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun initList() {
        scheduleStructureAdapter = TimetableStructureListAdapter(getThis(), mutableListOf())
        binding.scheduleStructure.adapter = scheduleStructureAdapter
        binding.scheduleStructure.layoutManager = LinearLayoutManager(getThis())
        binding.refresh.setColorSchemeColors(getColorPrimary())
        binding.refresh.setOnRefreshListener {
            ensureLoggedInForImport {
                refresh()
            }
        }
        scheduleStructureAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<TimePeriodInDay> {
            override fun onItemClick(data: TimePeriodInDay?, card: View?, position: Int) {
                if (data == null) return
                PopUpTimePeriodPicker().setInitialValue(data.from, data.to)
                    .setDialogTitle(R.string.pick_time_period)
                    .setOnDialogConformListener(object :
                        PopUpTimePeriodPicker.OnDialogConformListener {
                        override fun onClick(
                            timePeriodInDay: TimePeriodInDay
                        ) {
                            viewModel.setStructureData(timePeriodInDay, position)
                        }

                    }).show(supportFragmentManager, "pick")
            }

        })
    }

    private fun showTermPicker(terms: List<TermItem>) {
        val names = terms.map { getDisplayTermName(it) }
        PopUpCheckableList<TermItem>()
            .setListData(names, terms)
            .setTitle(getString(R.string.pick_import_term))
            .setOnConfirmListener(object : PopUpCheckableList.OnConfirmListener<TermItem> {
                override fun OnConfirm(title: String?, key: TermItem) {
                    viewModel.changeSelectedTerm(key)
                }
            }).show(supportFragmentManager, "terms")
    }

    private fun getDisplayTermName(term: TermItem): String {
        return TermNameFormatter.shortTermName(term.termName, term.name)
    }

    override fun initViewBinding(): ActivityEasImportBinding {
        return ActivityEasImportBinding.inflate(layoutInflater)
    }
}
