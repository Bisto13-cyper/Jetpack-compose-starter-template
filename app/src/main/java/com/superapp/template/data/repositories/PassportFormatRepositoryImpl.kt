package com.startup.template.data.repositories


import com.startup.template.data.mapper.PassportFormatMapper
import com.startup.template.data.network.PassportApiService
import com.startup.template.domain.Resource
import com.startup.template.domain.model.PassportFormatD
import com.startup.template.domain.repositories.PassportFormatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.let

class PassportFormatRepositoryImpl @Inject constructor(
    private val apiService: PassportApiService,
) : PassportFormatRepository {

    override suspend fun getPassportFormatList(): Flow<Resource<List<PassportFormatD>>> {
        return safeApiCall(
            apiCall = {
                val response = apiService.getPassportFormatsList()
                response

            },
            response = { apiResponse ->
                apiResponse.body()?.let {
                    PassportFormatMapper.mapToDomainList(it)
                } ?: emptyList()
            }
        )
    }
}