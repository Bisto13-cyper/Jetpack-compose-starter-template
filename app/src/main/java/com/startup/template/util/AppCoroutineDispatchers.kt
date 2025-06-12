package com.startup.template.util

import kotlinx.coroutines.CoroutineDispatcher

data class AppCoroutineDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val computation: CoroutineDispatcher,
)
