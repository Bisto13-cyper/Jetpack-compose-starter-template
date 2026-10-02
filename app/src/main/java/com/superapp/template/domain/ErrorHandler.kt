package com.startup.template.domain

import retrofit2.Response
import java.net.UnknownHostException

object ErrorHandler {

    fun <T> Response<T>.handleApiError(): Resource<Nothing> {
        return when (this.code()) {
            403 -> Resource.Error(ErrorType.FORBIDDEN, 403)
            401 -> Resource.Error(ErrorType.UNAUTHORIZED, 401)
            in 500..599 -> Resource.Error(ErrorType.INTERNAL_SERVER_ERROR, this.code())
            else -> Resource.Error(ErrorType.UNKNOWN, this.code())
        }
    }

    fun UnknownHostException.handleNetworkError(): Resource<Nothing> {
        return Resource.Error(ErrorType.NO_INTERNET, null)
    }

    fun Exception.handleUnknownError(): Resource<Nothing> {
        return Resource.Error(ErrorType.UNKNOWN, null)
    }
    fun Exception.handleIOError(): Resource<Nothing> {
        return Resource.Error(ErrorType.IO_EXCEPTION, null)
    }

}