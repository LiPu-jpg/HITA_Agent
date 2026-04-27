package com.limpu.hitax.di

import android.app.Application
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.source.preference.TimetablePreferenceSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(application: Application): AppDatabase {
        return AppDatabase.getDatabase(application)
    }

    @Provides
    @Singleton
    fun provideTimetablePreferenceSource(application: Application): TimetablePreferenceSource {
        return TimetablePreferenceSource.getInstance(application)
    }
}
