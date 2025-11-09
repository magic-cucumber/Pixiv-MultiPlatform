package top.kagg886.keqwest.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/9 21:22
 * ================================================
 */
internal class ReqwestClientEngine(override val config: ReqwestEngineConfig) : HttpClientEngineBase("ktor-reqwest") {
    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData = TODO()
}
