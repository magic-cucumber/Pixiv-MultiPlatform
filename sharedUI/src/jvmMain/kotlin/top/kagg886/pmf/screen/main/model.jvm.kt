package top.kagg886.pmf.screen.main

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformEngine(): HttpClientEngineFactory<*> = OkHttp
