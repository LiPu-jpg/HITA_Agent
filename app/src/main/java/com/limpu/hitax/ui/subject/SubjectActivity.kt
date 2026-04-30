package com.limpu.hitax.ui.subject

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.limpu.component.data.DataState
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.R
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentSession
import com.limpu.hitax.agent.subject.SubjectReadmeAgentFactory
import com.limpu.hitax.agent.subject.SubjectReadmeAgentInput
import com.limpu.hitax.agent.subject.SubjectReadmeAgentOutput
import com.limpu.hitax.data.model.resource.CourseReadmeData
import com.limpu.hitax.data.model.resource.CourseResourceItem
import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.repository.EASRepository
import com.limpu.hitax.data.repository.HoaRepository
import com.limpu.hitax.databinding.ActivitySubjectBinding
import com.limpu.hitax.ui.base.HiltBaseActivity
import com.limpu.hitax.ui.event.add.PopupAddEvent
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.CourseCodeUtils
import com.limpu.hitax.utils.CourseNameUtils
import com.limpu.hitax.utils.CourseResourceLinker
import com.limpu.hitax.utils.EditModeHelper
import com.limpu.hitax.utils.EventsUtils
import com.limpu.hitax.utils.TimeTools
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.widgets.PopUpFloatPicker
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.jsoup.Jsoup
import java.lang.StringBuilder
import java.net.URI
import java.text.DecimalFormat
import java.util.ArrayList
import java.util.Comparator
import java.util.regex.Pattern
import com.limpu.hitax.utils.LogUtils
import javax.inject.Inject

@AndroidEntryPoint
class SubjectActivity : HiltBaseActivity<ActivitySubjectBinding>(),
    EditModeHelper.EditableContainer<EventItem> {

    @Inject lateinit var easRepository: EASRepository
    @Inject lateinit var hoaRepository: HoaRepository

    protected val viewModel: SubjectViewModel by viewModels()
    var firstEnterCourse = true
    private lateinit var listAdapter: SubjectCoursesListAdapter
    lateinit var editModeHelper: EditModeHelper<EventItem>
    var isCourseExpanded = false

    private val hoaCampus by lazy { easRepository.getHoaCampus() }
    private val subjectMetaSupported by lazy { easRepository.isSubjectMetaSupported() }

    private val readmeAgentProvider: AgentProvider<SubjectReadmeAgentInput, SubjectReadmeAgentOutput> by lazy {
        SubjectReadmeAgentFactory.createProvider()
    }
    private var readmeAgentSession: AgentSession<SubjectReadmeAgentInput, SubjectReadmeAgentOutput>? = null

    private var readmeLiveData: LiveData<DataState<CourseReadmeData>>? = null
    private var readmeObserver: Observer<DataState<CourseReadmeData>>? = null
    private var readmeResolveKey: String? = null
    private var currentReadmeSource: String = ""
    private var selectedReadmeCandidate: CourseResourceItem? = null
    private lateinit var agentTraceAdapter: SubjectAgentTraceListAdapter

    private val markwon: Markwon by lazy {
        Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(JLatexMathPlugin.create(binding.readmeText.textSize))
            .usePlugin(GlideImagesPlugin.create(this))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _: View, link: String ->
                        openLink(resolveReadmeLink(link, currentReadmeSource))
                    }
                }
            })
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        initCourseList()
        initInfoList()
        initAgentTraceList()

        binding.cardType.title?.isSingleLine = false
        binding.cardType.title?.maxLines = 3
        binding.cardType.title?.ellipsize = TextUtils.TruncateAt.END
        binding.cardType.title?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        binding.cardType.title?.setHorizontallyScrolling(false)
        binding.cardType.title?.isSelected = false
        binding.cardType.setSubtitle("")

        if (!subjectMetaSupported) {
            binding.cardType.visibility = View.GONE
            binding.cardCredit.visibility = View.GONE
        }

        showReadmeLoading()

        viewModel.subjectLiveData.observe(this) { subject ->
            binding.collapse.title = CourseNameUtils.normalize(subject.name) ?: subject.name
            if (subjectMetaSupported) {
                binding.cardType.setTitle(getSubjectCategoryDisplay(subject))
                binding.cardCredit.setTitle(getSubjectCreditKey(subject))
            }
            loadReadmeForSubject(subject)
        }

        viewModel.classesLiveData.observe(this) {
            if (it.isEmpty()) {
                isCourseExpanded = false
                listAdapter.notifyItemChangedSmooth(it)
            } else if (isCourseExpanded) {
                val temp: MutableList<EventItem> = ArrayList(it)
                temp.add(EventItem.getTagInstance("less"))
                listAdapter.notifyItemChangedSmooth(temp)
            } else {
                val max = it.size.coerceAtMost(5)
                val temp: MutableList<EventItem> = ArrayList(it.subList(0, max))
                if (it.size > 5) temp.add(EventItem.getTagInstance("more"))
                if (max > 0) listAdapter.notifyItemChangedSmooth(
                    temp,
                    true
                ) { o1, o2 ->
                    if (o1.type === EventItem.TYPE.TAG && o2.type === EventItem.TYPE.TAG) 0 else o1.compareTo(
                        o2
                    )
                }
            }
            if (firstEnterCourse) {
                binding.subjectRecycler.scheduleLayoutAnimation()
                firstEnterCourse = false
            }
            var finished = 0
            var unfinished = 0
            for (ei in it) {
                if (TimeTools.passed(ei.to)) finished++ else unfinished++
            }
            val total = finished + unfinished
            if (total <= 0) {
                binding.subjectProgress.progress = 0
                binding.subjectProgressTitle.text = getString(R.string.percentage, 0)
                return@observe
            }
            val percentage = finished.toFloat() * 100.0f / total.toFloat()
            val va: ValueAnimator =
                ValueAnimator.ofInt(binding.subjectProgress.progress, percentage.toInt())
            va.duration = 700
            va.interpolator = DecelerateInterpolator(2f)
            va.addUpdateListener { animation ->
                binding.subjectProgress.progress = animation.animatedValue as Int
                binding.subjectProgressTitle.text =
                    getString(R.string.percentage, animation.animatedValue as Int)
            }
            va.startDelay = 160
            va.start()
        }

        viewModel.timetableLiveData.observe(this) { data ->
            binding.timetableName.text = data.name
            binding.timetableName.setTextColor(
                ContextCompat.getColor(this, when (TimeTools.getSeason(data.startTime.time)) {
                    TimeTools.SEASON.SPRING -> R.color.spring_text
                    TimeTools.SEASON.SUMMER -> R.color.summer_text
                    TimeTools.SEASON.AUTUMN -> R.color.autumn_text
                    else -> R.color.winter_text
                })
            )
            binding.timetableBg.setCardBackgroundColor(
                ContextCompat.getColor(this, when (TimeTools.getSeason(data.startTime.time)) {
                    TimeTools.SEASON.SPRING -> R.color.spring
                    TimeTools.SEASON.SUMMER -> R.color.summer
                    TimeTools.SEASON.AUTUMN -> R.color.autumn
                    else -> R.color.winter
                })
            )
            binding.timetableIcon.setImageResource(
                when (TimeTools.getSeason(data.startTime.time)) {
                    TimeTools.SEASON.SPRING -> R.drawable.season_spring
                    TimeTools.SEASON.SUMMER -> R.drawable.season_summer
                    TimeTools.SEASON.AUTUMN -> R.drawable.season_autumn
                    else -> R.drawable.season_winter
                }
            )
        }

        viewModel.teachersLiveData.observe(this) {
            val sb = StringBuilder()
            for (str in it) {
                sb.append(str).append(" ")
            }
            binding.cardTeacher.setTitle(sb.toString())
        }
    }

    private fun initAgentTraceList() {
        agentTraceAdapter = SubjectAgentTraceListAdapter()
        binding.agentTraceList.layoutManager = LinearLayoutManager(this)
        binding.agentTraceList.adapter = agentTraceAdapter
        binding.agentTraceList.visibility = View.GONE
    }

    private fun initInfoList() {
        binding.cardType.onCardClickListener = View.OnClickListener { }
        binding.cardTeacher.onCardClickListener = View.OnClickListener { }
        binding.cardTeacher.setOnLongClickListener {
            viewModel.teachersLiveData.value?.firstOrNull()?.let { teacher ->
                ActivityUtils.startTeacherHomepageSearch(getThis(), teacher)
            }
            true
        }
        binding.cardCredit.onCardClickListener = View.OnClickListener {
            if (!subjectMetaSupported) return@OnClickListener
            viewModel.subjectLiveData.value?.let {
                PopUpFloatPicker()
                    .setDialogTitle(R.string.subject_credit)
                    .setInitialValue(it.credit)
                    .setOnDialogConformListener(object : PopUpFloatPicker.OnDialogConformListener {
                        override fun onClick(result: Float) {
                            it.credit = result
                            viewModel.startSaveSubject()
                        }
                    })
                    .show(supportFragmentManager, "pick")
            }
        }
        binding.timetableCard.setOnClickListener {
            viewModel.timetableLiveData.value?.let {
                ActivityUtils.startTimetableDetailActivity(this, it.id)
            }
        }
        binding.buttonCourseContribute.setOnClickListener {
            viewModel.subjectLiveData.value?.let { subject ->
                val resolved = selectedReadmeCandidate
                val fallbackCode = CourseCodeUtils.normalize(subject.code)
                    ?.takeIf { it.isNotBlank() }
                    ?: subject.code?.takeIf { it.isNotBlank() }
                    ?: subject.name

                val repoName = resolved?.repoName
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackCode
                val courseName = resolved?.courseName
                    ?.takeIf { it.isNotBlank() }
                    ?: subject.name
                val courseCode = resolved?.courseCode
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackCode
                val repoType = resolved?.repoType
                    ?.takeIf { it.isNotBlank() }
                    ?: "normal"

                ActivityUtils.startCourseContributionActivity(
                    getThis(),
                    repoName,
                    courseName,
                    courseCode,
                    repoType,
                )
            }
        }
    }

    private fun loadReadmeForSubject(subject: TermSubject) {
        val key = "${subject.id}|${subject.code}|${subject.name}"
        if (key == readmeResolveKey) return
        readmeResolveKey = key
        showReadmeLoading()
        agentTraceAdapter.clear()
        binding.agentTraceList.visibility = View.GONE

        // 显示校区数据源提示
        updateCampusSourceHint()

        readmeAgentSession?.dispose()
        val session = readmeAgentProvider.createSession()
        readmeAgentSession = session
        session.run(
            input = SubjectReadmeAgentInput(
                owner = this,
                subjectId = subject.id,
                courseCode = subject.code,
                courseName = subject.name,
                campus = hoaCampus,
            ),
            onTrace = { trace ->
                // 只在DEBUG模式下显示trace信息
                LogUtils.d( "agent trace: stage=${trace.stage} message=${trace.message} payload=${trace.payload}")
                if (BuildConfig.DEBUG) {
                    runOnUiThread {
                        if (binding.agentTraceList.visibility != View.VISIBLE) {
                            binding.agentTraceList.visibility = View.VISIBLE
                        }
                        agentTraceAdapter.append(trace)
                        if (agentTraceAdapter.itemCount > 0) {
                            binding.agentTraceList.scrollToPosition(agentTraceAdapter.itemCount - 1)
                        }
                    }
                }
            },
            onResult = { result ->
                runOnUiThread {
                    if (!result.ok) {
                        LogUtils.d( "agent resolve failed: ${result.error}, fallback to direct resolver")
                        resolveReadmeCandidatesDirectly(subject)
                        return@runOnUiThread
                    }
                    handleResolvedReadmeCandidates(subject, result.data?.candidates.orEmpty())
                }
            }
        )
    }

    private fun resolveReadmeCandidatesDirectly(subject: TermSubject) {
        CourseResourceLinker.resolveCandidates(
            owner = this,
            courseCodeRaw = subject.code,
            courseNameRaw = subject.name,
            campus = hoaCampus,
        ) { candidates ->
            handleResolvedReadmeCandidates(subject, candidates)
        }
    }

    private fun handleResolvedReadmeCandidates(
        subject: TermSubject,
        candidates: List<CourseResourceItem>,
    ) {
        LogUtils.d(
            "loadReadmeForSubject: subjectId=${subject.id} code=${subject.code} name=${subject.name} " +
                "candidateCount=${candidates.size} candidates=${candidates.take(8).joinToString { "${it.repoType}|${it.repoName}|${it.courseCode}|${it.courseName}" }}"
        )
        when (candidates.size) {
            0 -> {
                selectedReadmeCandidate = null
                showReadmeMessage(getString(R.string.course_readme_missing))
                binding.readmeSource.text = ""
                clearReadmeObserver()
            }

            1 -> {
                val candidate = candidates.first()
                val repoName = candidate.repoName.trim()
                if (repoName.isBlank()) {
                    selectedReadmeCandidate = null
                    showReadmeMessage(getString(R.string.course_readme_missing))
                    binding.readmeSource.text = ""
                    clearReadmeObserver()
                } else {
                    LogUtils.d( "loadReadmeForSubject: auto-selected repo=$repoName")
                    selectedReadmeCandidate = candidate
                    observeReadme(repoName)
                }
            }

            else -> showReadmeCandidateChooser(candidates.take(3))
        }
    }

    private fun showReadmeCandidateChooser(candidates: List<CourseResourceItem>) {
        val labels = candidates.map {
            val code = it.courseCode.ifBlank { it.repoName }
            val name = it.courseName.ifBlank { code }
            "$code  $name"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("找到多个课程资源，请选择")
            .setItems(labels) { _, which ->
                val selected = candidates.getOrNull(which)
                val repoName = selected?.repoName?.trim().orEmpty()
                if (repoName.isBlank()) {
                    selectedReadmeCandidate = null
                    showReadmeMessage(getString(R.string.course_readme_missing))
                    binding.readmeSource.text = ""
                    clearReadmeObserver()
                    return@setItems
                }
                selectedReadmeCandidate = selected
                observeReadme(repoName)
            }
            .setNegativeButton("取消") { _, _ ->
                selectedReadmeCandidate = null
                showReadmeMessage(getString(R.string.course_readme_missing))
                binding.readmeSource.text = ""
                clearReadmeObserver()
            }
            .setCancelable(true)
            .show()
    }

    private fun observeReadme(repoName: String) {
        clearReadmeObserver()
        LogUtils.d( "observeReadme: repoName=$repoName")
        readmeLiveData = hoaRepository.getCourseReadme(repoName, hoaCampus)
        readmeObserver = Observer { state ->
            when (state.state) {
                DataState.STATE.NOTHING -> {
                    LogUtils.d( "observeReadme: loading")
                    showReadmeLoading()
                }
                DataState.STATE.SUCCESS -> {
                    binding.readmeProgress.visibility = View.GONE
                    val data = state.data
                    if (data == null) {
                        LogUtils.d( "observeReadme: success but data null")
                        showReadmeMessage(getString(R.string.course_resource_failed))
                        return@Observer
                    }
                    currentReadmeSource = data.source
                    binding.readmeSource.text = getString(R.string.course_readme_source, data.source)
                    LogUtils.d(
                        "observeReadme: source=${data.source} markdownLength=${data.markdown.length}"
                    )
                    val scoped = filterReadmeForCandidate(data.markdown, selectedReadmeCandidate)
                    LogUtils.d( "observeReadme: scopedMarkdownLength=${scoped.length}")
                    val processed = preprocessReadme(scoped)
                    markwon.setMarkdown(binding.readmeText, processed)
                    binding.readmeText.movementMethod = LinkMovementMethod.getInstance()
                }

                else -> {
                    val rawMessage = state.message?.trim().orEmpty()
                    val friendly = if (rawMessage.contains("invalid repo name", ignoreCase = true)) {
                        getString(R.string.course_readme_missing)
                    } else {
                        rawMessage.ifBlank { getString(R.string.course_resource_failed) }
                    }
                    LogUtils.d( "observeReadme: failed state=${state.state} msg=$rawMessage")
                    showReadmeMessage(friendly)
                }
            }
        }
        readmeLiveData?.observe(this, readmeObserver!!)
    }

    private fun clearReadmeObserver() {
        val live = readmeLiveData
        val observer = readmeObserver
        if (live != null && observer != null) {
            live.removeObserver(observer)
        }
        readmeLiveData = null
        readmeObserver = null
    }

    private fun showReadmeLoading() {
        binding.readmeProgress.visibility = View.VISIBLE
        binding.readmeSource.text = ""
        binding.readmeText.text = getString(R.string.course_readme_loading)
        binding.readmeText.movementMethod = null
    }

    private fun showReadmeMessage(message: String) {
        binding.readmeProgress.visibility = View.GONE
        binding.readmeText.text = message
        binding.readmeText.movementMethod = null
    }

    private data class ReadmeSection(
        val level: Int,
        val heading: String,
        val content: String,
    )

    private data class CourseMatchKeys(
        val codeTokens: List<String>,
        val nameKeys: List<String>,
    )

    private fun filterReadmeForCandidate(
        markdown: String,
        candidate: CourseResourceItem?,
    ): String {
        if (markdown.isBlank()) {
            LogUtils.d( "filterReadmeForCandidate: markdown blank")
            return markdown
        }

        val subject = viewModel.subjectLiveData.value
        val keys = buildCourseMatchKeys(candidate, subject)
        LogUtils.d(
            "filterReadmeForCandidate: subjectId=${subject?.id} subjectCode=${subject?.code} " +
                "subjectName=${subject?.name} candidateRepo=${candidate?.repoName} " +
                "candidateCode=${candidate?.courseCode} candidateName=${candidate?.courseName}"
        )
        LogUtils.d( "filterReadmeForCandidate keys: codeTokens=${keys.codeTokens} nameKeys=${keys.nameKeys}")
        logMarkdownNeighborhood(markdown, "软体机器人理论与技术")

        if (keys.codeTokens.isEmpty() && keys.nameKeys.isEmpty()) {
            LogUtils.d( "filterReadmeForCandidate: keys empty, return raw markdown")
            return markdown
        }

        extractByTomlCourseMeta(markdown, keys)?.let {
            LogUtils.d( "filterReadmeForCandidate: matched by TOML meta")
            return it
        }

        val sections = splitReadmeSections(markdown)
        LogUtils.d( "filterReadmeForCandidate: section count=${sections.size}")
        logSectionHeadings(sections, keys)
        if (sections.size <= 1) {
            LogUtils.d( "filterReadmeForCandidate: no structured sections, return raw markdown")
            return markdown
        }

        val matched = sections.firstOrNull {
            it.heading.isNotBlank() && sectionMatchesKeys(it.heading, keys)
        }

        if (matched == null) {
            LogUtils.d( "filterReadmeForCandidate: no heading matched, return raw markdown")
            return markdown
        }

        LogUtils.d( "filterReadmeForCandidate: matched heading='${matched.heading}' level=${matched.level}")
        return buildString {
            append("#".repeat(matched.level.coerceAtLeast(2)))
            append(" ")
            append(matched.heading)
            append("\n")
            append(matched.content.trim())
        }.trim()
    }

    private fun splitReadmeSections(markdown: String): List<ReadmeSection> {
        val headingRegex = Regex("(?m)^(#{2,4})\\s+(.+)$")
        val matches = headingRegex.findAll(markdown).toList()
        if (matches.isEmpty()) {
            return listOf(ReadmeSection(level = 0, heading = "", content = markdown))
        }

        val sections = mutableListOf<ReadmeSection>()
        val intro = markdown.substring(0, matches.first().range.first)
        sections.add(ReadmeSection(level = 0, heading = "", content = intro))

        for ((index, match) in matches.withIndex()) {
            val level = match.groupValues[1].length
            val heading = match.groupValues[2].trim()
            val start = match.range.last + 1
            val end = findSectionEnd(markdown, matches, index, level)
            val content = markdown.substring(start, end)
            sections.add(ReadmeSection(level = level, heading = heading, content = content))
        }
        return sections
    }

    private fun findSectionEnd(
        markdown: String,
        headings: List<MatchResult>,
        currentIndex: Int,
        currentLevel: Int,
    ): Int {
        for (i in (currentIndex + 1) until headings.size) {
            val level = headings[i].groupValues[1].length
            if (level <= currentLevel) {
                return headings[i].range.first
            }
        }
        return markdown.length
    }

    private fun logMarkdownNeighborhood(markdown: String, keyword: String) {
        val index = markdown.indexOf(keyword)
        if (index < 0) {
            LogUtils.d( "markdown neighborhood: keyword '$keyword' not found")
            return
        }
        val start = (index - 240).coerceAtLeast(0)
        val end = (index + keyword.length + 240).coerceAtMost(markdown.length)
        val snippet = markdown.substring(start, end)
            .replace("\n", "\\n")
            .replace("\r", "")
        LogUtils.d( "markdown neighborhood around '$keyword': $snippet")
    }

    private fun logSectionHeadings(sections: List<ReadmeSection>, keys: CourseMatchKeys) {
        sections.forEachIndexed { index, section ->
            if (section.heading.isBlank()) return@forEachIndexed
            val hit = sectionMatchesKeys(section.heading, keys)
            LogUtils.d( "section[$index] L${section.level} heading='${section.heading}' matched=$hit")
        }
    }

    private fun buildCourseMatchKeys(
        candidate: CourseResourceItem?,
        subject: TermSubject?,
    ): CourseMatchKeys {
        val subjectCodeRaw = subject?.code?.trim().orEmpty()
        val subjectCodeNormalized = CourseCodeUtils.normalize(subject?.code)?.trim().orEmpty()
        val subjectNameKey = CourseNameUtils.normalizeKey(subject?.name)

        val codeTokens = buildList {
            if (subjectCodeRaw.isNotBlank()) add(subjectCodeRaw.lowercase())
            if (subjectCodeNormalized.isNotBlank()) add(subjectCodeNormalized.lowercase())

            if (subjectCodeRaw.isBlank() && subjectCodeNormalized.isBlank()) {
                candidate?.courseCode?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
                candidate?.courseCode?.let { CourseCodeUtils.normalize(it) }?.takeIf { it.isNotBlank() }
                    ?.let { add(it.lowercase()) }
                candidate?.repoName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
            }
        }.distinct()

        val nameKeys = buildList {
            if (subjectNameKey.isNotBlank()) {
                add(subjectNameKey)
            }
            candidate?.courseName?.let { CourseNameUtils.normalizeKey(it) }
                ?.takeIf { it.isNotBlank() }?.let { add(it) }
            candidate?.aliases
                ?.map { CourseNameUtils.normalizeKey(it) }
                ?.filter { it.isNotBlank() }
                ?.forEach { add(it) }
        }.distinct()

        return CourseMatchKeys(codeTokens = codeTokens, nameKeys = nameKeys)
    }

    private fun extractByTomlCourseMeta(markdown: String, keys: CourseMatchKeys): String? {
        val headingRegex = Regex("(?m)^(#{2,4})\\s+(.+)$")
        val headings = headingRegex.findAll(markdown).toList()
        if (headings.isEmpty()) {
            LogUtils.d( "extractByTomlCourseMeta: no headings")
            return null
        }

        val courseMetaRegex = Regex("(?m)^\\s*<!--\\s*TOML-COURSE:\\s*([^>]*)-->\\s*$")

        for ((index, heading) in headings.withIndex()) {
            val level = heading.groupValues[1].length
            val title = heading.groupValues[2].trim()
            val bodyStart = heading.range.last + 1
            val bodyEnd = findSectionEnd(markdown, headings, index, level)
            val body = markdown.substring(bodyStart, bodyEnd)

            val attrs = courseMetaRegex.find(body)?.groupValues?.getOrNull(1)
            if (attrs == null) {
                LogUtils.d( "extractByTomlCourseMeta: heading='$title' has no TOML-COURSE")
                continue
            }
            val code = parseTomlAttr(attrs, "code")
            val name = parseTomlAttr(attrs, "name")
            val tomlHit = tomlCourseMatches(code, name, keys)
            val headingHit = sectionMatchesKeys(title, keys)
            LogUtils.d(
                "extractByTomlCourseMeta: heading='$title' code='$code' name='$name' tomlHit=$tomlHit headingHit=$headingHit"
            )

            if (!tomlHit && !headingHit) {
                continue
            }

            val cleanedBody = body
                .replace(courseMetaRegex, "")
                .trim()

            LogUtils.d( "extractByTomlCourseMeta: selected heading='$title' level=$level")
            return buildString {
                append("#".repeat(level.coerceAtLeast(2)))
                append(" ")
                append(title)
                if (cleanedBody.isNotBlank()) {
                    append("\n")
                    append(cleanedBody)
                }
            }.trim()
        }

        LogUtils.d( "extractByTomlCourseMeta: no TOML section matched")
        return null
    }

    private fun parseTomlAttr(attrs: String, key: String): String {
        val regex = Regex("\\b$key\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(attrs)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun tomlCourseMatches(codeRaw: String, nameRaw: String, keys: CourseMatchKeys): Boolean {
        val code = codeRaw.trim().lowercase()
        if (code.isNotBlank()) {
            val normalizedCode = CourseCodeUtils.normalize(code)?.lowercase().orEmpty()
            if (keys.codeTokens.any { token ->
                    token == code ||
                        token == normalizedCode ||
                        code.contains(token) ||
                        token.contains(code) ||
                        (normalizedCode.isNotBlank() && (token.contains(normalizedCode) || normalizedCode.contains(token)))
                }
            ) {
                return true
            }
        }

        val nameKey = CourseNameUtils.normalizeKey(nameRaw)
        if (nameKey.isNotBlank()) {
            if (keys.nameKeys.any { key ->
                    key == nameKey || key.contains(nameKey) || nameKey.contains(key)
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun sectionMatchesKeys(heading: String, keys: CourseMatchKeys): Boolean {
        val headingKey = CourseNameUtils.normalizeKey(heading)
        val headingLower = heading.lowercase()
        if (headingLower.contains("crossspecialty")) return false

        if (keys.codeTokens.any { token ->
                token.isNotBlank() && (headingLower.contains(token) || token.contains(headingLower))
            }
        ) {
            return true
        }

        if (headingKey.isBlank()) return false
        return keys.nameKeys.any { key ->
            key == headingKey || headingKey.contains(key) || key.contains(headingKey)
        }
    }

    private fun preprocessReadme(markdown: String): String {
        val withTables = convertHtmlTables(markdown)
        val startPattern = Regex("\\{\\{[%<]\\s*details\\s+([^%>]+)\\s*[%>]\\}\\}", RegexOption.IGNORE_CASE)
        val endPattern = Regex("\\{\\{[%<]\\s*/details\\s*[%>]\\}\\}", RegexOption.IGNORE_CASE)
        val replacedStart = startPattern.replace(withTables) { match ->
            val attrs = match.groupValues[1]
            val title = Regex("title\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1) ?: getString(R.string.course_resource_open)
            val closed = Regex("closed\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()
            val openAttr = if (closed == "true") "" else " open"
            "<details$openAttr><summary>$title</summary>"
        }
        return endPattern.replace(replacedStart, "</details>")
    }

    private fun convertHtmlTables(markdown: String): String {
        val tablePattern = Regex("(?is)<table[^>]*>.*?</table>")
        return tablePattern.replace(markdown) { match ->
            runCatching {
                val doc = Jsoup.parse(match.value)
                val table = doc.selectFirst("table") ?: return@replace match.value
                val rows = table.select("tr")
                if (rows.isEmpty()) return@replace match.value
                val cellsList = rows.map { row ->
                    row.select("th,td").map { it.text().trim() }
                }
                val maxCols = cellsList.maxOfOrNull { it.size } ?: 0
                if (maxCols == 0) return@replace match.value

                fun pad(row: List<String>): List<String> {
                    if (row.size >= maxCols) return row
                    return row + List(maxCols - row.size) { "" }
                }

                val header = pad(cellsList.first())
                val headerRow = header.joinToString(" | ")
                val separator = List(maxCols) { "---" }.joinToString(" | ")
                val body = cellsList.drop(1).joinToString("\n") { row ->
                    pad(row).joinToString(" | ")
                }
                listOf(headerRow, separator, body).filter { it.isNotBlank() }.joinToString("\n")
            }.getOrDefault(match.value)
        }
    }

    private fun resolveReadmeLink(link: String, source: String): String {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link
        }
        val base = source.trim()
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return runCatching { URI(base).resolve(link).toString() }.getOrDefault(link)
        }
        return link
    }

    private fun openLink(link: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(intent)
        }
    }

    private fun getSubjectTypeName(type: TermSubject.TYPE): String {
        return when (type) {
            TermSubject.TYPE.MOOC -> getString(R.string.subject_mooc)
            TermSubject.TYPE.COM_A -> getString(R.string.subject_exam)
            else -> getString(R.string.not_counted_in_GPA)
        }
    }

    private fun getSubjectCategoryDisplay(subject: TermSubject): String {
        val line1 = subject.selectCategory?.takeIf { it.isNotBlank() } ?: getString(R.string.none)
        val line2 = subject.field?.takeIf { it.isNotBlank() } ?: getString(R.string.none)
        val line3 = subject.nature?.takeIf { it.isNotBlank() } ?: getSubjectTypeName(subject.type)
        return listOf(line1, line2, line3).joinToString("\n")
    }

    private fun getSubjectCreditKey(subject: TermSubject): String {
        val pt = Pattern.compile("[0-9]*[.]*[0-9]*")
        val matcher = pt.matcher(subject.credit.toString())
        val df = DecimalFormat("0.0")
        if (matcher.find()) {
            val c = matcher.group(0) ?: return "0.0"
            if (TextUtils.isEmpty(c)) return "0.0"
            val d = java.lang.Double.valueOf(c)
            return df.format(d)
        }
        return "0.0"
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when (editModeHelper.isEditMode) {
            true -> editModeHelper.closeEditMode()
            else -> super.onBackPressed()
        }
    }

    private fun initCourseList() {
        listAdapter = SubjectCoursesListAdapter(this, mutableListOf())
        binding.subjectRecycler.adapter = listAdapter
        binding.subjectRecycler.layoutManager = ChipsLayoutManager.newBuilder(this)
            .setOrientation(ChipsLayoutManager.HORIZONTAL)
            .setMaxViewsInRow(4)
            .build()
        listAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<EventItem> {
            override fun onItemClick(data: EventItem?, card: View?, position: Int) {
                data?.let {
                    if (it.type === EventItem.TYPE.TAG) {
                        toggleCourseExpand()
                    } else {
                        EventsUtils.showEventItem(getThis(), it)
                    }
                }
            }
        })
        listAdapter.setOnItemLongClickListener(object :
            BaseListAdapter.OnItemLongClickListener<EventItem> {
            override fun onItemLongClick(data: EventItem?, view: View?, position: Int): Boolean {
                if (editModeHelper.isEditMode) return false
                editModeHelper.activateEditMode(position)
                return true
            }
        })
        editModeHelper = EditModeHelper(this, listAdapter, this)
        editModeHelper.init(this, R.id.edit_bar, R.layout.edit_mode_bar_2)
        editModeHelper.smoothSwitch = true
        editModeHelper.closeEditMode()
        binding.courseAdd.setOnClickListener {
            viewModel.subjectLiveData.value?.let { ts ->
                viewModel.timetableLiveData.value?.let { tt ->
                    PopupAddEvent().setInitTimetable(tt).setInitSubject(ts)
                        .show(supportFragmentManager, "add_event")
                }
            }
        }
    }

    fun toggleCourseExpand() {
        val comparator: Comparator<EventItem> = Comparator<EventItem> { o1, o2 ->
            if (o1.type === EventItem.TYPE.TAG && o2.type === EventItem.TYPE.TAG) 0
            else o1.compareTo(o2)
        }
        val refreshJudge: BaseListAdapter.RefreshJudge<EventItem> =
            object : BaseListAdapter.RefreshJudge<EventItem> {
                override fun judge(oldData: EventItem, newData: EventItem): Boolean {
                    return newData.type === EventItem.TYPE.TAG
                }
            }
        viewModel.classesLiveData.value?.let {
            isCourseExpanded = if (isCourseExpanded) {
                val max = it.size.coerceAtMost(5)
                val temp: MutableList<EventItem> = ArrayList(it.subList(0, max))
                if (it.size > 5) temp.add(EventItem.getTagInstance("more"))
                if (max > 0) listAdapter.notifyItemChangedSmooth(temp, refreshJudge, comparator)
                false
            } else {
                val temp: MutableList<EventItem> = ArrayList(it)
                temp.add(EventItem.getTagInstance("less"))
                listAdapter.notifyItemChangedSmooth(temp, refreshJudge, comparator)
                true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        intent.getStringExtra("subjectId")?.let { viewModel.startRefresh(it) }
    }

    override fun onDestroy() {
        clearReadmeObserver()
        readmeAgentSession?.dispose()
        readmeAgentSession = null
        super.onDestroy()
    }

    override fun initViewBinding(): ActivitySubjectBinding {
        return ActivitySubjectBinding.inflate(layoutInflater)
    }

    /**
     * 更新校区数据源提示
     * 当用户是本部或威海校区时，提示课程资源来自深圳
     */
    private fun updateCampusSourceHint() {
        val easCampus = easRepository.getEasToken().campus
        val shouldShowHint = when (easCampus) {
            EASToken.Campus.BENBU, EASToken.Campus.WEIHAI -> true
            else -> false
        }

        if (shouldShowHint) {
            binding.campusSourceHint.visibility = View.VISIBLE
            binding.campusSourceHint.text = when (easCampus) {
                EASToken.Campus.BENBU -> "💡 课程资源来自深圳校区（本部数据）"
                EASToken.Campus.WEIHAI -> "💡 课程资源来自深圳校区（威海数据）"
                else -> "💡 课程资源来自深圳校区"
            }
        } else {
            binding.campusSourceHint.visibility = View.GONE
        }
    }

    override fun onEditClosed() {
    }

    override fun onEditStarted() {
    }

    override fun onItemCheckedChanged(position: Int, checked: Boolean, currentSelected: Int) {
    }

    override fun onDelete(toDelete: Collection<EventItem>?) {
        toDelete?.let { viewModel.deleteCourses(it) }
        editModeHelper.closeEditMode()
    }
}
