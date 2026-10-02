package com.startup.template.data.model

import kotlinx.serialization.Serializable


@Serializable
data class PassportFormatResponse(
    val nationality: String? = null,
    val format: String? = null,
    val example: String? = null
)




