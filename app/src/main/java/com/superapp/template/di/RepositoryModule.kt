package com.startup.template.di


import com.startup.template.data.network.PassportApiService
import com.startup.template.data.repositories.PassportFormatRepositoryImpl
import com.startup.template.domain.repositories.PassportFormatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun providePassportRepository(apiService: PassportApiService): PassportFormatRepository {
        return PassportFormatRepositoryImpl(apiService)
    }

}