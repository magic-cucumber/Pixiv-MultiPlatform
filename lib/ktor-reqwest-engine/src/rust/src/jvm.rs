#![cfg(feature = "jvm")]
use jni::{JNIEnv, objects::{JByteBuffer, JClass}, sys::jint};
use jni_fn::jni_fn;

// #[unsafe(no_mangle)]
// #[allow(non_snake_case)]
// #[jni_fn("top.kagg886.keqwest.NativeBridgeKt")]
// pub fn encode(env: JNIEnv, _class: JClass, buffer: JByteBuffer, limit: jint) {
// }
