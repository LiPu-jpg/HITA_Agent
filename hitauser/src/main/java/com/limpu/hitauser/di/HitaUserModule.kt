package com.limpu.hitauser.di

import android.content.Context
import com.limpu.hitauser.data.UserDatabase
import com.limpu.hitauser.data.source.preference.UserPreferenceSource
import com.limpu.hitauser.data.source.web.ManagerWebSource
import com.limpu.hitauser.data.source.web.ProfileWebSource
import com.limpu.hitauser.data.source.web.UserWebSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HitaUserModule {

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase {
        return UserDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideUserPreferenceSource(@ApplicationContext context: Context): UserPreferenceSource {
        return UserPreferenceSource(context.applicationContext)
    }

    @Provides
    @Singleton
    fun provideUserWebSource(@ApplicationContext context: Context): UserWebSource {
        return UserWebSource(context.applicationContext)
    }

    @Provides
    @Singleton
    fun provideProfileWebSource(@ApplicationContext context: Context): ProfileWebSource {
        return ProfileWebSource(context.applicationContext)
    }

    @Provides
    @Singleton
    fun provideManagerWebSource(@ApplicationContext context: Context): ManagerWebSource {
        return ManagerWebSource(context.applicationContext)
    }
}
