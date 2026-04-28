package com.limpu.hitax.di

import android.content.Context
import com.limpu.hitax.agent.document.FileParserDispatcher
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

    @Provides
    @Singleton
    fun provideStaticWebSource(@ApplicationContext context: Context): StaticWebSource = StaticWebSource(context)

    @Provides
    @Singleton
    fun provideGitHubWebSource(@ApplicationContext context: Context): GitHubWebSource = GitHubWebSource(context)

    @Provides
    @Singleton
    fun provideFileParserDispatcher(): FileParserDispatcher = FileParserDispatcher()
}
