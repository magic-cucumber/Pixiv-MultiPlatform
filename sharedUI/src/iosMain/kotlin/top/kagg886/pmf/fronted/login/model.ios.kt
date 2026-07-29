package top.kagg886.pmf.fronted.login

import io.ktor.client.engine.darwin.Darwin
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pixko.PixivVerification

actual fun createPixivVerification(): PixivVerification<*> = PixivAccountFactory.newAccount(Darwin)
