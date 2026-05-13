#![cfg(feature = "jvm")]
use crate::encode_animated_image_unsafe;
use jni::{EnvUnowned, errors::{LogErrorAndDefault, Result}, jni_mangle, objects::{JByteBuffer, JClass}, sys::jint};

#[allow(non_snake_case)]
#[jni_mangle("moe.tarsin.gif.NativeBridgeKt")]
pub fn encode<'local>(mut env: EnvUnowned<'local>, _class: JClass, buffer: JByteBuffer, limit: jint) {
    env.with_env(|env| -> Result<_> {
        let ptr = env.get_direct_buffer_address(&buffer)?;
        encode_animated_image_unsafe(ptr, limit);
        Ok(())
    })
    .resolve::<LogErrorAndDefault>();
}
