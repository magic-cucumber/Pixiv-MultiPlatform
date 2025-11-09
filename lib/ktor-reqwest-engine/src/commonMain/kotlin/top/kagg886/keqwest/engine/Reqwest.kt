package top.kagg886.keqwest.engine

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/9 21:19
 * ================================================
 */

public data object Reqwest: HttpClientEngineFactory<ReqwestEngineConfig> {

    override fun create(block: ReqwestEngineConfig.() -> Unit): HttpClientEngine {

    }

}
