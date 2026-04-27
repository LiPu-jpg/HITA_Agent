package com.limpu.hitax.ui.eas.score

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import android.os.Build
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.CourseScoreItem
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.data.source.preference.ScoreReminderStore
import com.limpu.hitax.data.work.ScoreReminderScheduler
import com.limpu.hitax.data.source.web.service.EASService
import com.limpu.hitax.databinding.ActivityEasScoreFirstBinding
import com.limpu.hitax.ui.eas.EASActivity
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.widgets.PopUpCheckableList
import com.limpu.hitax.utils.TermNameFormatter
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels

@AndroidEntryPoint
class ScoreInquiryActivity :
    EASActivity<ScoreInquiryViewModel, ActivityEasScoreFirstBinding>() {

    override val viewModel: ScoreInquiryViewModel by viewModels()
    lateinit var listAdapter: ScoresListAdapter
    private lateinit var scoreReminderStore: ScoreReminderStore
    private var scoreQueryInFlight = false

    // 通知权限请求 launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableScoreReminder()
        } else {
            binding.scoreReminderSwitch.isChecked = false
            scoreReminderStore.setEnabled(false)
            Toast.makeText(this, "需要通知权限才能发送成绩提醒", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    private fun bindLiveData() {
        viewModel.termsLiveData.observe(this) { data ->
            when (data.state) {
                DataState.STATE.SUCCESS -> {
                    if (!data.data.isNullOrEmpty()) {
                        for (t in data.data!!) {
                            if (t.isCurrent) {
                                viewModel.selectedTermLiveData.value = t
                                return@observe
                            }
                        }
                        viewModel.selectedTermLiveData.value = data.data?.get(0)
                    }
                }

                DataState.STATE.NOT_LOGGED_IN -> {
                    if (!handleSessionExpired {
                            scoreQueryInFlight = true
                            binding.refresh.isRefreshing = true
                            viewModel.startRefresh()
                            true
                        }) {
                        scoreQueryInFlight = false
                    }
                }

                DataState.STATE.NOTHING -> Unit

                else -> {
                    binding.refresh.isRefreshing = false
                    binding.schoolSemesterText.setText(R.string.load_failed)
                }
            }
        }
        viewModel.selectedTermLiveData.observe(this) {
            it?.let { term ->
                binding.refresh.isRefreshing = true
                binding.schoolSemesterText.text = getDisplayTermName(term)
            }
        }
        viewModel.scoresLiveData.observe(this) {
            binding.refresh.isRefreshing = false
            val latestItems = it.data ?: emptyList()
            when (it.state) {
                DataState.STATE.SUCCESS -> {
                    scoreQueryInFlight = false
                    resetSessionRetryState()
                    listAdapter.notifyItemChangedSmooth(latestItems)
                }

                DataState.STATE.NOT_LOGGED_IN -> {
                    listAdapter.notifyItemChangedSmooth(emptyList())
                    if (scoreQueryInFlight) {
                        if (!handleSessionExpired { retryCurrentScoreQuery() }) {
                            scoreQueryInFlight = false
                        }
                    }
                }

                else -> {
                    scoreQueryInFlight = false
                    resetSessionRetryState()
                    listAdapter.notifyItemChangedSmooth(emptyList())
                }
            }
            binding.emptyView.visibility = if (latestItems.isNotEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        viewModel.scoreSummaryLiveData.observe(this) { summary ->
            val hasSummary = summary != null && (
                summary.gpa.isNotBlank() || summary.rank.isNotBlank() || summary.total.isNotBlank()
            )
            binding.scoreSummaryCard.visibility = if (hasSummary) View.VISIBLE else View.GONE
            val gpaRaw = summary?.gpa?.ifBlank { "-" } ?: "-"
            val gpa = gpaRaw.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: gpaRaw
            val rank = summary?.rank?.ifBlank { "-" } ?: "-"
            val total = summary?.total?.ifBlank { "" } ?: ""
            binding.scoreGpaValue.text = gpa
            binding.scoreRankValue.text = if (total.isNotBlank() && rank.isNotBlank() && rank != "-") {
                "$rank / $total"
            } else {
                rank
            }
        }
        viewModel.selectedTestTypeLiveData.observe(this) {
            it?.let {
                binding.refresh.isRefreshing = true
                binding.testTypeText.text = when (it) {
                    EASService.TestType.NORMAL -> getString(R.string.test_type_final)
                    EASService.TestType.RESIT -> getString(R.string.test_type_midterm)
                    EASService.TestType.RETAKE -> getString(R.string.test_type_retake)
                    EASService.TestType.ALL -> getString(R.string.test_type_final)
                }
            }
        }
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        scoreReminderStore = ScoreReminderStore(applicationContext)
        binding.scoreReminderSwitch.isChecked = scoreReminderStore.isEnabled()
        binding.scoreReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 开启时检查通知权限（仅Android 13+需要）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        enableScoreReminder()
                    } else {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    enableScoreReminder()
                }
            } else {
                scoreReminderStore.setEnabled(false)
                ScoreReminderScheduler.cancel(this)
            }
        }
        binding.scoreReminderCard.setOnClickListener {
            val next = !binding.scoreReminderSwitch.isChecked
            binding.scoreReminderSwitch.isChecked = next
        }
        binding.refresh.setColorSchemeColors(getColorPrimary())
        binding.refresh.setOnRefreshListener {
            scoreQueryInFlight = true
            refresh()
        }
        listAdapter = ScoresListAdapter(this, mutableListOf())
        binding.scoreStructure.adapter = listAdapter
        binding.scoreStructure.layoutManager = LinearLayoutManager(getThis())
        binding.schoolSemesterLayout.setOnClickListener {
            viewModel.termsLiveData.value?.data?.let { terms ->
                val names = terms.map { getDisplayTermName(it) }
                if (names.isEmpty()) return@setOnClickListener
                PopUpCheckableList<TermItem>()
                    .setListData(names, terms)
                    .setTitle(getString(R.string.pick_quety_term))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<TermItem> {
                        override fun OnConfirm(title: String?, key: TermItem) {
                            scoreQueryInFlight = true
                            viewModel.selectedTermLiveData.value = key
                        }
                    }).show(supportFragmentManager, "terms")
            }
        }
        binding.testTypeLayout.setOnClickListener {
            val names = mutableListOf(
                getString(R.string.test_type_final),
                getString(R.string.test_type_midterm)
            )
            val list = arrayListOf(
                EASService.TestType.NORMAL,
                EASService.TestType.RESIT
            )
            PopUpCheckableList<EASService.TestType>()
                .setListData(names, list)
                .setTitle(getString(R.string.pick_test_type))
                .setOnConfirmListener(object :
                    PopUpCheckableList.OnConfirmListener<EASService.TestType> {
                    override fun OnConfirm(title: String?, key: EASService.TestType) {
                        scoreQueryInFlight = true
                        viewModel.selectedTestTypeLiveData.value = key
                    }
                }).show(supportFragmentManager, "types")
        }
        listAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<CourseScoreItem> {
            override fun onItemClick(data: CourseScoreItem?, card: View?, position: Int) {
                data?.let {
                    ScoreDetailFragment(it).show(supportFragmentManager, "score_detail")
                }
            }
        })
        viewModel.selectedTestTypeLiveData.value = EASService.TestType.NORMAL
    }

    private fun retryCurrentScoreQuery(): Boolean {
        if (viewModel.retryCurrentQuery()) {
            scoreQueryInFlight = true
            binding.refresh.isRefreshing = true
            return true
        }
        return false
    }

    private fun getDisplayTermName(term: TermItem): String {
        return TermNameFormatter.shortTermName(term.termName, term.name)
    }

    override fun initViewBinding(): ActivityEasScoreFirstBinding {
        return ActivityEasScoreFirstBinding.inflate(layoutInflater)
    }

    override fun refresh() {
        binding.refresh.isRefreshing = true
        scoreQueryInFlight = true
        viewModel.startRefresh()
    }

    /**
     * 开启成绩提醒
     */
    private fun enableScoreReminder() {
        scoreReminderStore.setEnabled(true)
        ScoreReminderScheduler.schedule(this)
        Toast.makeText(this, "成绩提醒已开启", Toast.LENGTH_SHORT).show()
    }
}
