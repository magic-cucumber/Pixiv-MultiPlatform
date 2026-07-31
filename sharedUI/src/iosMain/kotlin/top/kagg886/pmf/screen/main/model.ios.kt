package top.kagg886.pmf.screen.main

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformEngine(): HttpClientEngineFactory<*> = Darwin
