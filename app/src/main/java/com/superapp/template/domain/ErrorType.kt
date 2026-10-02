package com.startup.template.domain

import com.startup.template.R


enum class ErrorType(val errorMessageResId: Int) {
    NO_INTERNET(R.string.error_no_internet),
    FORBIDDEN(R.string.error_forbidden),
    UNAUTHORIZED(R.string.error_unauthorized),
    UNKNOWN(R.string.error_unknown),
    API_RESPONSE_NULL(R.string.error_no_data),
    INTERNAL_SERVER_ERROR(R.string.error_internal_server),
    IO_EXCEPTION(R.string.error_no_internet),
}