package top.kagg886.pmf.util

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformEngine(): HttpClientEngineFactory<*> = Darwin
