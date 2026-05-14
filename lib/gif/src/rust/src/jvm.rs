#![cfg(feature = "jvm")]
use crate::encode_animated_image_unsafe;
use jni::{Env, EnvUnowned, errors::{Result, ThrowRuntimeExAndDefault}, jni_mangle, objects::{JByteBuffer, JClass}, sys::jint};

fn enter_jni<'local, T: Default>(mut env: EnvUnowned<'local>, f: impl FnOnce(&mut Env) -> Result<T>) -> T {
    env.with_env(f).resolve::<ThrowRuntimeExAndDefault>()
}

#[allow(non_snake_case)]
#[jni_mangle("moe.tarsin.gif.NativeBridgeKt")]
pub fn encode<'local>(env: EnvUnowned<'local>, _class: JClass, buffer: JByteBuffer, limit: jint) {
    enter_jni(env, |env| {
        let ptr = env.get_direct_buffer_address(&buffer)?;
        encode_animated_image_unsafe(ptr, limit);
        Ok(())
    })
}
