package com.limpu.hitax.di

import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.timetable.TimetableAgentFactory
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideTimetableAgentProvider(): AgentProvider<TimetableAgentInput, TimetableAgentOutput> {
        return TimetableAgentFactory.createProvider()
    }
}
