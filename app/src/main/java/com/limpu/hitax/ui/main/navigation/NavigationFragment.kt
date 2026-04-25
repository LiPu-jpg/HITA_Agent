package com.limpu.hitax.ui.main.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import com.limpu.hitax.R
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.data.source.preference.CourseReminderStore
import com.limpu.hitax.data.work.CourseReminderScheduler
import com.limpu.hitax.databinding.FragmentNavigationBinding
import com.limpu.hitax.ui.eas.classroom.EmptyClassroomActivity
import com.limpu.hitax.ui.eas.exam.ExamActivity
import com.limpu.hitax.ui.eas.imp.ImportTimetableActivity
import com.limpu.hitax.ui.eas.login.PopUpLoginEAS
import com.limpu.hitax.ui.eas.score.ScoreInquiryActivity
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.ActivityUtils.CourseResourceMode
import com.limpu.hitax.utils.IcsImportUtils
import com.limpu.stupiduser.data.repository.LocalUserRepository
import com.limpu.style.base.BaseFragment

class NavigationFragment : BaseFragment<NavigationViewModel, FragmentNavigationBinding>() {
    private val easTokenObserver = Observer<com.limpu.hitax.data.model.eas.EASToken> {
        refreshEasUserCard()
    }

    // ICS 文件选择器
    private val selectIcsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importICS(it) }
    }

    // 通知权限请求 launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，开启课程提醒
            enableCourseReminder()
        } else {
            // 权限被拒绝，关闭开关并提示
            binding?.switchCourseReminder?.isChecked = false
            Toast.makeText(requireContext(), "需要通知权限才能发送课程提醒", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun getViewModelClass(): Class<NavigationViewModel> {
        return NavigationViewModel::class.java
    }

    override fun initViews(view: View) {
        viewModel.recentTimetableLiveData.observe(this) {
            if (it == null) {
                binding?.recentSubtitle?.setText(R.string.none)
            } else {
                binding?.recentSubtitle?.text = it.name
            }
        }
        viewModel.timetableCountLiveData.observe(this) {
            if (it == 0) {
                binding?.timetableSubtitle?.setText(R.string.no_timetable)
            } else {
                binding?.timetableSubtitle?.text = getString(R.string.timetable_count_format, it)
            }

        }
        binding?.cardTimetable?.setOnClickListener {
            ActivityUtils.startTimetableManager(requireContext())
        }
        binding?.cardRecentTimetable?.setOnClickListener {
            viewModel.recentTimetableLiveData.value?.let {
                ActivityUtils.startTimetableDetailActivity(requireContext(), it.id)
            }
        }
        binding?.cardImport?.setOnClickListener {
            ActivityUtils.startActivity(
                requireContext(),
                ImportTimetableActivity::class.java
            )
        }
        binding?.cardEmptyClassroom?.setOnClickListener {
            ActivityUtils.startActivity(
                requireContext(),
                EmptyClassroomActivity::class.java
            )
        }
        binding?.cardScores?.setOnClickListener {
            ActivityUtils.startActivity(
                requireContext(),
                ScoreInquiryActivity::class.java
            )
        }
        binding?.cardSubjects?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow(
                requireContext(),
                directTo = ExamActivity::class.java,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        ActivityUtils.startActivity(
                            requireContext(),
                            ExamActivity::class.java
                        )
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {

                    }
                }
            )
        }
        binding?.cardCourseLookup?.setOnClickListener {
            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.VIEW)
        }
        binding?.cardCourseSubmitPr?.setOnClickListener {
            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.SUBMIT)
        }
        
        // 课程提醒开关
        val reminderStore = CourseReminderStore.getInstance(requireContext())
        binding?.switchCourseReminder?.isChecked = reminderStore.isEnabled()
        binding?.switchCourseReminder?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 开启时检查通知权限（仅Android 13+需要）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        // 已有权限，直接开启
                        enableCourseReminder()
                    } else {
                        // 请求权限
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    // Android 13以下不需要权限，直接开启
                    enableCourseReminder()
                }
            } else {
                // 关闭课程提醒
                reminderStore.setEnabled(false)
                CourseReminderScheduler.autoSchedule(requireContext())
                Toast.makeText(requireContext(), "课程提醒已关闭", Toast.LENGTH_SHORT).show()
            }
        }
        binding?.cardCourseReminder?.setOnClickListener {
            binding?.switchCourseReminder?.toggle()
        }
        
        // ICS 导入
        binding?.cardIcsImport?.setOnClickListener {
            selectIcsLauncher.launch(IcsImportUtils.pickerMimeTypes())
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        viewModel.startRefresh()
        activity?.application?.let {
            EASRepository.getInstance(it).observeEasToken().observe(viewLifecycleOwner, easTokenObserver)
        }
        refreshEasUserCard()
    }

    private fun refreshEasUserCard() {
        LocalUserRepository.getInstance(requireContext()).getLoggedInUser().let {
            if (it.isValid()) { //如果已登录
                binding?.avatar?.let { it1 ->
                    com.limpu.stupiduser.util.ImageUtils.loadAvatarInto(
                        requireContext(),
                        it.avatar,
                        it1
                    )
                }
                binding?.avatar?.let { it1 ->
                    com.limpu.stupiduser.util.ImageUtils.loadAvatarInto(
                        requireContext(),
                        it.avatar,
                        it1
                    )
                }
                binding?.username?.text = it.username
                binding?.nickname?.text = it.nickname
                binding?.userCard?.setOnClickListener { _ ->
                    ActivityUtils.startProfileActivity(
                        requireContext(),
                        it.id,
                        binding?.avatar
                    )
                }
            } else {
                val easToken = activity?.application?.let { app -> EASRepository.getInstance(app).getEasToken() }
                if (easToken?.isLogin() == true) {
                    binding?.username?.text = easToken.name?.ifBlank { easToken.stuId?.ifBlank { easToken.username } }
                        ?: easToken.stuId?.ifBlank { easToken.username }
                        ?: easToken.username
                        ?: getString(R.string.eas_account_not_logged_in_title)
                    binding?.nickname?.text = buildString {
                        val primary = easToken.stuId?.trim().orEmpty()
                        val secondary = listOf(
                            easToken.school,
                            easToken.major,
                            easToken.grade,
                            easToken.className
                        ).mapNotNull { info -> info?.trim()?.takeIf(String::isNotBlank) }
                            .joinToString(" · ")
                        append(primary)
                        if (secondary.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(secondary)
                        }
                    }.ifBlank { easToken.username.orEmpty() }
                } else {
                    binding?.username?.setText(R.string.eas_account_not_logged_in_title)
                    binding?.nickname?.setText(R.string.eas_account_not_logged_in_subtitle)
                }
                binding?.avatar?.setImageResource(R.drawable.place_holder_avatar)
                binding?.userCard?.setOnClickListener {
                    ActivityUtils.showEasVerifyWindow<Activity>(
                        requireContext(),
                        directTo = null,
                        onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                            override fun onSuccess(window: PopUpLoginEAS) {
                                window.dismiss()
                            }

                            override fun onFailed(window: PopUpLoginEAS) {}
                        }
                    )
                }
            }
        }
    }

    override fun initViewBinding(): FragmentNavigationBinding {
        return FragmentNavigationBinding.inflate(layoutInflater)
    }

    override fun onStop() {
        activity?.application?.let {
            EASRepository.getInstance(it).observeEasToken().removeObserver(easTokenObserver)
        }
        super.onStop()
    }

    /**
     * 导入 ICS 文件
     */
    private fun importICS(uri: Uri) {
        val context = requireContext()

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(context, "无法读取所选 ICS 文件", Toast.LENGTH_SHORT).show()
                return
            }
            val timetableRepo = TimetableRepository.getInstance(context.applicationContext as android.app.Application)

            timetableRepo.importFromICSAsNewTimetable(
                inputStream,
                IcsImportUtils.getDisplayName(context, uri)
            )
                .observe(this) { result ->
                    when (result.state) {
                        com.limpu.component.data.DataState.STATE.SUCCESS -> {
                            val importResult = result.data ?: return@observe
                            Toast.makeText(
                                context,
                                "已创建课表“${importResult.timetableName}”，导入 ${importResult.importedCount} 个课程",
                                Toast.LENGTH_SHORT
                            ).show()
                            ActivityUtils.startTimetableDetailActivity(
                                context,
                                importResult.timetableId
                            )
                        }
                        com.limpu.component.data.DataState.STATE.FETCH_FAILED -> {
                            Toast.makeText(context, "导入失败: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 开启课程提醒
     */
    private fun enableCourseReminder() {
        val reminderStore = CourseReminderStore.getInstance(requireContext())
        reminderStore.setEnabled(true)
        CourseReminderScheduler.autoSchedule(requireContext())
        Toast.makeText(requireContext(), "课程提醒已开启（上课前10分钟提醒）", Toast.LENGTH_SHORT).show()
    }
}
