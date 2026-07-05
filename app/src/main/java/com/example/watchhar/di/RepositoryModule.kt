package com.example.watchhar.di

import com.example.watchhar.data.AudioRepositoryImpl
import com.example.watchhar.data.ML.IMUEventDetectorImpl
import com.example.watchhar.data.ML.MultimodalClassifierImpl
import com.example.watchhar.data.SensorRepositoryImpl
import com.example.watchhar.domain.ml.IMUEventDetectorRepository
import com.example.watchhar.domain.ml.MultimodalClassifierRepository
import com.example.watchhar.domain.repository.AudioRepository
import com.example.watchhar.domain.repository.SensorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindSensorRepository(
        impl: SensorRepositoryImpl
    ): SensorRepository

    @Binds
    @Singleton
    fun bindAudioRepository(
        impl: AudioRepositoryImpl
    ): AudioRepository

    @Binds
    @Singleton
    fun bindMultimodalClassifier(
        impl: MultimodalClassifierImpl
    ): MultimodalClassifierRepository

    @Binds
    @Singleton
    fun bindIMUEventDetector(
        impl: IMUEventDetectorImpl
    ): IMUEventDetectorRepository
}