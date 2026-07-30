package top.kagg886.pmf.ui.login

import io.ktor.client.engine.okhttp.OkHttp
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pixko.PixivVerification

actual fun createPixivVerification(): PixivVerification<*> = PixivAccountFactory.newAccount(OkHttp)
