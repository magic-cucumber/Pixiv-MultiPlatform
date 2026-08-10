package top.kagg886.pmf.util

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 15:30
 * ================================================
 */

expect fun createPlatformEngine(): HttpClientEngineFactory<*>
