package com.limpu.hitax.ui.eas.classroom

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.databinding.ActivityEasClassroomBinding
import com.limpu.style.base.BaseListAdapter
import com.limpu.component.data.DataState
import com.limpu.hitax.ui.eas.EASActivity
import com.limpu.hitax.ui.eas.classroom.detail.EmptyClassroomDetailFragment
import com.limpu.style.widgets.PopUpCheckableList
import com.limpu.hitax.utils.TermNameFormatter
import java.util.*

class EmptyClassroomActivity :
    EASActivity<EmptyClassroomViewModel, ActivityEasClassroomBinding>() {
    lateinit var listAdapter: EmptyClassroomListAdapter
    private var classroomQueryInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }


    override fun initViewBinding(): ActivityEasClassroomBinding {
        return ActivityEasClassroomBinding.inflate(layoutInflater)
    }

    override fun getViewModelClass(): Class<EmptyClassroomViewModel> {
        return EmptyClassroomViewModel::class.java
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
                    if (classroomQueryInFlight) {
                        if (!handleSessionExpired {
                                retryCurrentClassroomQuery()
                            }) {
                            classroomQueryInFlight = false
                            binding.refresh.isRefreshing = false
                        }
                    }
                }

                else -> {
                    binding.refresh.isRefreshing = false
                    binding.termText.setText(R.string.load_failed)
                }
            }
        }
        viewModel.buildingsLiveData.observe(this) { data ->
            when (data.state) {
                DataState.STATE.SUCCESS -> {
                    if (!data.data.isNullOrEmpty()) {
                        viewModel.selectedBuildingLiveData.value = data.data?.get(0)
                    }
                }

                DataState.STATE.NOT_LOGGED_IN -> {
                    if (classroomQueryInFlight) {
                        if (!handleSessionExpired {
                                retryCurrentClassroomQuery()
                            }) {
                            classroomQueryInFlight = false
                            binding.refresh.isRefreshing = false
                        }
                    }
                }

                else -> {
                    binding.refresh.isRefreshing = false
                    binding.buildingText.setText(R.string.load_failed)
                }
            }
        }
        viewModel.selectedBuildingLiveData.observe(this) {
            binding.refresh.isRefreshing = true
            binding.buildingText.text = it.name
        }
        viewModel.selectedTermLiveData.observe(this) {
            binding.refresh.isRefreshing = true
            binding.termText.text = getDisplayTermName(it)
        }
        viewModel.selectedWeekLiveData.observe(this) {
            binding.refresh.isRefreshing = true
            binding.weekText.text = getString(R.string.week_title, it)
        }
        viewModel.classroomLiveData.observe(this) {
            binding.refresh.isRefreshing = false
            when (it.state) {
                DataState.STATE.SUCCESS -> {
                    classroomQueryInFlight = false
                    resetSessionRetryState()
                    it.data?.let { it1 -> listAdapter.notifyItemChangedSmooth(it1) }
                }

                DataState.STATE.NOT_LOGGED_IN -> {
                    if (classroomQueryInFlight) {
                        if (!handleSessionExpired { retryCurrentClassroomQuery() }) {
                            classroomQueryInFlight = false
                        }
                    }
                }

                else -> {
                    classroomQueryInFlight = false
                    resetSessionRetryState()
                }
            }
        }

    }

    override fun refresh() {
        binding.refresh.isRefreshing = true
        classroomQueryInFlight = true
        viewModel.startRefresh()
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        binding.refresh.setColorSchemeColors(getColorPrimary())
        listAdapter = EmptyClassroomListAdapter(this, mutableListOf(), viewModel)
        binding.refresh.setOnRefreshListener {
            classroomQueryInFlight = true
            refresh()
        }
        binding.list.adapter = listAdapter
        binding.list.layoutManager = GridLayoutManager(this, 2)
        binding.termLayout.setOnClickListener {
            viewModel.termsLiveData.value?.data?.let { terms ->
                val names = terms.map { getDisplayTermName(it) }
                if (names.isEmpty()) return@setOnClickListener
                PopUpCheckableList<TermItem>()
                    .setListData(names, terms)
                    .setTitle(getString(R.string.pick_quety_term))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<TermItem> {
                        override fun OnConfirm(title: String?, key: TermItem) {
                            classroomQueryInFlight = true
                            viewModel.selectedTermLiveData.value = key
                        }
                    }).show(supportFragmentManager, "terms")
            }
        }

        binding.buildingLayout.setOnClickListener {
            val names = mutableListOf<String>()
            viewModel.buildingsLiveData.value?.data?.let {
                for (i in it) {
                    i.name?.let { it1 -> names.add(it1) }
                }
                if (names.isEmpty()) {
                    return@setOnClickListener
                }
                PopUpCheckableList<BuildingItem>()
                    .setListData(names, it)
                    .setTitle(getString(R.string.pick_query_building))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<BuildingItem> {
                        override fun OnConfirm(title: String?, key: BuildingItem) {
                            classroomQueryInFlight = true
                            viewModel.selectedBuildingLiveData.value = key
                        }
                    }).show(supportFragmentManager, "building")
            }
        }
        binding.weekLayout.setOnClickListener {
            val names = mutableListOf<String>()
            for (i in 1..20) {
                names.add(i.toString())
            }
            PopUpCheckableList<Int>()
                .setListData(names, (1..20).toList())
                .setTitle(getString(R.string.pick_query_week))
                .setOnConfirmListener(object :
                    PopUpCheckableList.OnConfirmListener<Int> {
                    override fun OnConfirm(title: String?, key: Int) {
                        classroomQueryInFlight = true
                        viewModel.selectedWeekLiveData.value = key
                    }
                }).show(supportFragmentManager, "terms")

        }
        listAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<ClassroomItem> {
            override fun onItemClick(data: ClassroomItem?, card: View?, position: Int) {
                data?.let { classroom->
                    viewModel.selectedTermLiveData.value?.let { term->
                        viewModel.selectedWeekLiveData.value?.let { week->
                            viewModel.timetableStructureLiveData.value?.data?.let { structure->
                                EmptyClassroomDetailFragment(
                                    term,week,classroom,structure
                                ).show(supportFragmentManager,"detail")
                            }

                        }

                    }
                }


            }
        })
    }

    private fun retryCurrentClassroomQuery(): Boolean {
        val started = viewModel.retryCurrentQuery()
        if (started) {
            classroomQueryInFlight = true
            binding.refresh.isRefreshing = true
        } else {
            refresh()
        }
        return true
    }

    private fun getDisplayTermName(term: TermItem): String {
        return TermNameFormatter.shortTermName(term.termName, term.name)
    }
}
