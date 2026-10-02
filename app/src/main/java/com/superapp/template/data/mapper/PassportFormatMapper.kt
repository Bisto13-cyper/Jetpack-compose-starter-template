package com.startup.template.data.mapper



import com.startup.template.data.model.PassportFormatResponse
import com.startup.template.domain.model.PassportFormatD
import kotlin.collections.map
import kotlin.text.orEmpty

object PassportFormatMapper {

    // Convert a PassportFormatResponse (DTO) to a PassportFormatD (Domain Model)
    fun PassportFormatResponse.mapToDomain(): PassportFormatD {
        return PassportFormatD(
            nationality = nationality.orEmpty(),
            format = format.orEmpty(),
            example = example.orEmpty()
        )
    }

    // Convert a list of PassportFormatResponse (DTO) to a list of PassportFormatD (Domain Model)
    fun mapToDomainList(userModels: List<PassportFormatResponse>): List<PassportFormatD> {
        return userModels.map { it.mapToDomain() }
    }

}
