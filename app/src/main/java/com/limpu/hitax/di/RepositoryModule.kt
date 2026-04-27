package com.limpu.hitax.di

import android.app.Application
import android.content.Context
import com.limpu.hitax.agent.document.FileParserDispatcher
import com.limpu.hitax.data.repository.*
import com.limpu.hitax.data.source.preference.*
import com.limpu.hitax.data.source.web.GitHubWebSource
import com.limpu.hitax.data.source.web.StaticWebSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // ── Repositories ──

    @Provides
    @Singleton
    fun provideTimetableRepository(app: Application): TimetableRepository = TimetableRepository(app)

    @Provides
    @Singleton
    fun provideSubjectRepository(app: Application): SubjectRepository = SubjectRepository(app)

    @Provides
    @Singleton
    fun provideEASRepository(
        app: Application,
        easPreferenceSource: EasPreferenceSource,
        timetablePreferenceSource: TimetablePreferenceSource
    ): EASRepository = EASRepository(app, easPreferenceSource, timetablePreferenceSource)

    @Provides
    @Singleton
    fun provideHoaRepository(): HoaRepository = HoaRepository()

    @Provides
    @Singleton
    fun provideStaticRepository(app: Application, staticWebSource: StaticWebSource): StaticRepository = StaticRepository(app, staticWebSource)

    @Provides
    @Singleton
    fun provideAdditionalRepository(app: Application): AdditionalRepository = AdditionalRepository(app)

    @Provides
    @Singleton
    fun provideEasSettingsRepository(app: Application): EasSettingsRepository = EasSettingsRepository(app)

    @Provides
    @Singleton
    fun provideTeacherInfoRepository(app: Application): TeacherInfoRepository = TeacherInfoRepository(app)

    @Provides
    @Singleton
    fun provideUpdateRepository(@ApplicationContext context: Context, githubWebSource: GitHubWebSource): UpdateRepository = UpdateRepository(context, githubWebSource)

    @Provides
    @Singleton
    fun provideTimetableStyleRepository(app: Application): TimetableStyleRepository = TimetableStyleRepository(app)

    // ── Preference Stores ──

    @Provides
    @Singleton
    fun provideScoreReminderStore(@ApplicationContext context: Context): ScoreReminderStore = ScoreReminderStore(context)

    @Provides
    @Singleton
    fun provideCourseReminderStore(@ApplicationContext context: Context): CourseReminderStore = CourseReminderStore(context)

    @Provides
    @Singleton
    fun provideTimetablePreferenceSource(@ApplicationContext context: Context): TimetablePreferenceSource = TimetablePreferenceSource(context)

    @Provides
    @Singleton
    fun provideEasPreferenceSource(@ApplicationContext context: Context): EasPreferenceSource = EasPreferenceSource(context)

    @Provides
    @Singleton
    fun provideBenbuStartDatePreferenceSource(@ApplicationContext context: Context): BenbuStartDatePreferenceSource = BenbuStartDatePreferenceSource(context)

    // ── Web Sources ──

    @Provides
    @Singleton
    fun provideStaticWebSource(@ApplicationContext context: Context): StaticWebSource = StaticWebSource(context)

    @Provides
    @Singleton
    fun provideGitHubWebSource(@ApplicationContext context: Context): GitHubWebSource = GitHubWebSource(context)

    // ── Agent / Document ──

    @Provides
    @Singleton
    fun provideFileParserDispatcher(): FileParserDispatcher = FileParserDispatcher()
}
