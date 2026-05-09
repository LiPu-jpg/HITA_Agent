package com.limpu.hitax.ui.eas.exam

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.eas.ExamItem
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivityEasExamBinding
import com.limpu.hitax.ui.eas.EASActivity
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.widgets.PopUpCheckableList
import com.limpu.hitax.utils.TermNameFormatter
import com.limpu.hitax.utils.TermUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExamActivity :
    EASActivity<ExamViewModel, ActivityEasExamBinding>() {

    override val viewModel: ExamViewModel by viewModels()
    lateinit var listAdapter: ExamListAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.limpu.hitax.utils.LogUtils.d("📱 ExamActivity: onCreate called", "ExamActivity")
        setToolbarActionBack(binding.toolbar)
    }

    private fun bindLiveData(){
        viewModel.termsLiveData.observe(this) { data ->
            if (data.state == DataState.STATE.SUCCESS) {
                if (!data.data.isNullOrEmpty()) {
                    for (t in data.data!!) {
                        if (t.isCurrent) {
                            viewModel.selectedTermLiveData.value = t
                            return@observe
                        }
                    }
                    viewModel.selectedTermLiveData.value = data.data?.get(0)
                }
            } else if (data.state == DataState.STATE.NOT_LOGGED_IN) {
                // 处理认证过期，触发自动重新登录
                if (!handleSessionExpired {
                    refresh()
                    true
                }) {
                    binding.refresh.isRefreshing = false
                    binding.examTermText.setText(R.string.load_failed)
                }
            } else {
                binding.refresh.isRefreshing = false
                binding.examTermText.setText(R.string.load_failed)
            }
        }
        viewModel.selectedTermLiveData.observe(this) {
            it?.let { term ->
                binding.refresh.isRefreshing = true
                binding.examTermText.text = getDisplayTermName(term)
                // 显示加载提示，避免用户以为卡住了
                if (viewModel.examInfoLiveData.value?.data.isNullOrEmpty()) {
                    Toast.makeText(this, "正在加载考试数据，请稍候...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewModel.selectedExamTypeLiveData.observe(this) {
            it?.let { type ->
                binding.refresh.isRefreshing = true
                binding.examTypeText.text = when (type) {
                    ExamViewModel.ExamType.ALL -> getString(R.string.exam_type_all)
                    ExamViewModel.ExamType.MIDTERM -> getString(R.string.exam_type_midterm)
                    ExamViewModel.ExamType.FINAL -> getString(R.string.exam_type_final)
                }
            }
        }
        viewModel.examInfoLiveData.observe(this){
            com.limpu.hitax.utils.LogUtils.d("📱 ExamActivity: received state=${it.state}, data size=${it.data?.size}", "ExamActivity")
            binding.refresh.isRefreshing = false
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { it1 ->
                    com.limpu.hitax.utils.LogUtils.d("📱 ExamActivity: updating adapter with ${it1.size} items", "ExamActivity")
                    listAdapter.notifyDataSetChanged(it1)
                }
                // 有数据时显示导入按钮，隐藏空视图
                val hasData = (it.data?.size ?: 0) > 0
                binding.btnImportAll.visibility = if (hasData) View.VISIBLE else View.GONE
                binding.emptyView.visibility = if (hasData) View.GONE else View.VISIBLE
            } else if (it.state == DataState.STATE.NOT_LOGGED_IN) {
                // 处理认证过期，触发自动重新登录
                if (!handleSessionExpired {
                    refresh()
                    true
                }) {
                    // 加载失败时显示空视图
                    binding.emptyView.visibility = View.VISIBLE
                }
            } else {
                // 加载失败或未完成时显示空视图
                binding.emptyView.visibility = View.VISIBLE
            }
        }
    }
    override fun refresh() {
        binding.refresh.isRefreshing = true
        resetSessionRetryState() // 重置会话重试状态
        Toast.makeText(this, "正在加载考试数据，请稍候...", Toast.LENGTH_SHORT).show()
        viewModel.startRefresh()
    }

    override fun initViewBinding(): ActivityEasExamBinding {
        return ActivityEasExamBinding.inflate(layoutInflater)
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        binding.refresh.setColorSchemeColors(getColorPrimary())
        listAdapter = ExamListAdapter(this, mutableListOf())
        binding.refresh.setOnRefreshListener {
            refresh()
        }

        binding.examStructure.adapter = listAdapter
        binding.examStructure.layoutManager = LinearLayoutManager(getThis())

        // 设置导入按钮点击监听
        binding.btnImportAll.setOnClickListener {
            importAllExams()
        }

        binding.examTermLayout.setOnClickListener {
            viewModel.termsLiveData.value?.data?.let { terms ->
                // 使用公共工具类过滤学期：只显示最近的学期
                val filteredTerms = TermUtils.filterRecentTerms(terms)

                val names = filteredTerms.map { getDisplayTermName(it) }
                if (names.isEmpty()) return@setOnClickListener
                PopUpCheckableList<TermItem>()
                    .setListData(names, filteredTerms)
                    .setTitle(getString(R.string.pick_exam_term))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<TermItem> {
                        override fun OnConfirm(title: String?, key: TermItem) {
                            viewModel.selectedTermLiveData.value = key
                        }
                    }).show(supportFragmentManager, "exam_terms")
            }
        }
        // 根据校区决定是否显示考试类型筛选
        // 深圳校区：考试无期中期末分类，不显示筛选器
        // 本部：有期中期末分类，显示筛选器
        if (viewModel.shouldShowExamTypeFilter()) {
            binding.examTypeLayout.setOnClickListener {
                val names = mutableListOf(
                    getString(R.string.exam_type_all),
                    getString(R.string.exam_type_midterm),
                    getString(R.string.exam_type_final)
                )
                val list = arrayListOf(
                    ExamViewModel.ExamType.ALL,
                    ExamViewModel.ExamType.MIDTERM,
                    ExamViewModel.ExamType.FINAL
                )
                PopUpCheckableList<ExamViewModel.ExamType>()
                    .setListData(names, list)
                    .setTitle(getString(R.string.pick_exam_type))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<ExamViewModel.ExamType> {
                        override fun OnConfirm(title: String?, key: ExamViewModel.ExamType) {
                            viewModel.selectedExamTypeLiveData.value = key
                        }
                    }).show(supportFragmentManager, "exam_types")
            }
        } else {
            // 深圳校区隐藏考试类型选择器
            binding.examTypeLayout.visibility = View.GONE
        }
        listAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<ExamItem> {
            override fun onItemClick(data: ExamItem?, card: View?, position: Int) {
                data?.let {
                    ExamDetailFragment(it).show(supportFragmentManager, "exam_detail")
                }
            }
        })
        viewModel.selectedExamTypeLiveData.value = ExamViewModel.ExamType.ALL
    }

    private fun getDisplayTermName(term: TermItem): String {
        return TermNameFormatter.shortTermName(term.termName, term.name)
    }

    /**
     * 一键导入全部考试到默认课表
     *
     * 功能：
     * 1. 获取当前显示的所有考试
     * 2. 过滤掉已导入的考试
     * 3. 批量导入到默认课表
     * 4. 显示导入进度和结果
     */
    /**
     * 一键导入全部考试到默认课表
     *
     * 功能：
     * 1. 获取当前显示的所有考试
     * 2. 过滤掉已导入的考试
     * 3. 批量导入到默认课表
     * 4. 显示导入进度和结果
     */
    private fun importAllExams() {
        viewModel.examInfoLiveData.value?.let { state ->
            if (state.state != com.limpu.component.data.DataState.STATE.SUCCESS) {
                com.limpu.hitax.utils.LogUtils.d("⚠️ No exam data to import", "ExamActivity")
                return
            }

            val exams = state.data ?: return
            if (exams.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_exams_to_import), Toast.LENGTH_SHORT).show()
                return
            }

            // 显示加载动画和提示
            binding.btnImportAll.startAnimation()
            Toast.makeText(this, getString(R.string.importing_exams), Toast.LENGTH_SHORT).show()

            // 在后台线程执行导入
            Thread {
                val result = viewModel.importAllExams(exams)

                // 切换到主线程显示结果
                runOnUiThread {
                    // 根据导入结果显示成功/失败动画
                    val success = result.successCount == result.totalCount
                    val partialSuccess = result.successCount > 0 && result.successCount < result.totalCount
                    val allImported = result.successCount == 0 && result.skippedCount > 0

                    when {
                        success -> {
                            // 全部成功
                            com.limpu.hitax.utils.AnimationUtils.loadingButtonDone(
                                binding.btnImportAll,
                                success = true,
                                toast = false
                            )
                            Toast.makeText(
                                this,
                                getString(R.string.import_exams_success, result.successCount),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        partialSuccess -> {
                            // 部分成功
                            com.limpu.hitax.utils.AnimationUtils.loadingButtonDone(
                                binding.btnImportAll,
                                success = true,
                                toast = false
                            )
                            Toast.makeText(
                                this,
                                getString(R.string.import_exams_partial, result.successCount, result.totalCount),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        allImported -> {
                            // 全部已导入
                            com.limpu.hitax.utils.AnimationUtils.loadingButtonDone(
                                binding.btnImportAll,
                                success = true,
                                toast = false
                            )
                            Toast.makeText(this, getString(R.string.all_exams_imported), Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // 全部失败
                            com.limpu.hitax.utils.AnimationUtils.loadingButtonDone(
                                binding.btnImportAll,
                                success = false,
                                toast = false
                            )
                        }
                    }

                    if (result.successCount > 0) {
                        // 刷新考试列表，更新已导入状态
                        // 这里可以添加一个标记来显示哪些考试已导入
                    }
                }
            }.start()
        }
    }
}
