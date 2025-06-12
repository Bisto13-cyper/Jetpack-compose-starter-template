package com.startup.template.data.network

import com.startup.template.data.model.PassportFormatResponse
import retrofit2.Response
import retrofit2.http.GET

interface PassportApiService {
    @GET("passportFormats")
    suspend fun getPassportFormatsList(): Response<List<PassportFormatResponse>>
}