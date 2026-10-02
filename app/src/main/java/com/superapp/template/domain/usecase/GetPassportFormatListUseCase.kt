package com.startup.template.domain.usecase


import com.startup.template.domain.Resource
import com.startup.template.domain.model.PassportFormatD
import com.startup.template.domain.repositories.PassportFormatRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetPassportFormatListUseCase @Inject constructor(
    private val repository: PassportFormatRepository
) {
    suspend operator fun invoke(): Flow<Resource<List<PassportFormatD>>> {
        return repository.getPassportFormatList()
    }
}