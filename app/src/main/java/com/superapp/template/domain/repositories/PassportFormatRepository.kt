package com.startup.template.domain.repositories

import com.startup.template.domain.Resource
import com.startup.template.domain.model.PassportFormatD
import kotlinx.coroutines.flow.Flow


interface PassportFormatRepository {
    suspend fun getPassportFormatList(): Flow<Resource<List<PassportFormatD>>>

}