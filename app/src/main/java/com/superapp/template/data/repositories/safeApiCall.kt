package com.startup.template.data.repositories

import com.startup.template.domain.ErrorHandler.handleApiError
import com.startup.template.domain.ErrorHandler.handleIOError
import com.startup.template.domain.ErrorHandler.handleNetworkError
import com.startup.template.domain.ErrorHandler.handleUnknownError
import com.startup.template.domain.ErrorType
import com.startup.template.domain.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

inline fun <T, R> safeApiCall(
    crossinline apiCall: suspend () -> Response<T>,
    crossinline response: (Response<T>) -> R
): Flow<Resource<R>> = flow {
    try {
        emit(Resource.Loading)
        val apiResponse = apiCall()
        if (apiResponse.isSuccessful) {
            val data = apiResponse.body()

            if (data != null) {
                val mappedData = response(apiResponse)
                emit(Resource.Success(mappedData))
            } else {
                emit(Resource.Error(ErrorType.API_RESPONSE_NULL, apiResponse.code()))
            }
        } else {
            emit(apiResponse.handleApiError())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        emit(e.handleIOError())
    } catch (e: UnknownHostException) {
        emit(e.handleNetworkError())
    } catch (e: Exception) {
        emit(e.handleUnknownError())
    }
}
