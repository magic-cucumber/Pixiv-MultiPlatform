#![cfg(feature = "jvm")]
use jni::errors::{LogErrorAndDefault, Result};
use jni::objects::JObject;
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::jstring;
use jni::{Env, EnvUnowned, jni_mangle};
use rfd::{AsyncFileDialog, FileHandle};

pub struct FFIClosure {
    f: Box<dyn FnOnce() -> Option<FileHandle>>,
}

fn enter_jni<'local, T: Default>(mut env: EnvUnowned<'local>, f: impl FnOnce(&mut Env) -> Result<T>) -> T {
    env.with_env(|env| -> Result<_> { f(env) }).resolve::<LogErrorAndDefault>()
}

#[allow(non_snake_case)]
#[jni_mangle("top.kagg886.filepicker.internal.NativeFilePicker")]
pub fn openFileSaver(env: EnvUnowned<'_>, _class: JClass, suggested_name: JString, extension: JString, directory: JString) -> *mut FFIClosure {
    enter_jni(env, |env| {
        let suggested_name = suggested_name.try_to_string(env)?;
        let dir = if directory.is_null() { String::from("~") } else { directory.try_to_string(env)? };
        let mut dialog = AsyncFileDialog::new().set_directory(dir).set_file_name(suggested_name);
        if !extension.is_null() {
            let ext: String = extension.try_to_string(env)?;
            dialog = dialog.add_filter("file", &[ext])
        }
        let fut = dialog.save_file();
        let f = Box::new(|| futures::executor::block_on(fut));
        let bo = Box::new(FFIClosure { f });
        Ok(Box::into_raw(bo))
    })
}

#[allow(non_snake_case)]
#[jni_mangle("top.kagg886.filepicker.internal.NativeFilePicker")]
pub fn awaitPointer(env: EnvUnowned, _class: JClass, ptr: *mut FFIClosure) -> jstring {
    enter_jni(env, |env| {
        let f = unsafe { Box::from_raw(ptr) };
        let hnd = (f.f)().map(|x| x.path().display().to_string());
        hnd.map_or(Ok(*JObject::null()), |s| Ok(*JObject::from(env.new_string(s)?)))
    })
}

#[allow(non_snake_case)]
#[jni_mangle("top.kagg886.filepicker.internal.NativeFilePicker")]
pub fn openFilePicker(env: EnvUnowned, _class: JClass, ext: JObjectArray<JString>, title: JString, directory: JString) -> *mut FFIClosure {
    enter_jni(env, |env| {
        let dir = if directory.is_null() { String::from("~") } else { directory.try_to_string(env)? };
        let mut dialog = AsyncFileDialog::new().set_directory(dir);
        if !ext.is_null() {
            let mut vec = Vec::new();
            let len = ext.len(env)?;
            for i in 0..len {
                let jstr = ext.get_element(env, i)?;
                let rust_str: String = jstr.try_to_string(env)?;
                vec.push(rust_str);
            }
            if !vec.is_empty() {
                dialog = dialog.add_filter("filter", &vec);
            }
        }
        if !title.is_null() {
            let title: String = title.try_to_string(env)?;
            dialog = dialog.set_title(title)
        }
        let fut = dialog.pick_file();
        let f = Box::new(|| futures::executor::block_on(fut));
        let bo = Box::new(FFIClosure { f });
        Ok(Box::into_raw(bo))
    })
}

#[allow(non_snake_case)]
#[jni_mangle("top.kagg886.filepicker.internal.NativeFilePicker")]
pub fn openDictionaryPicker(env: EnvUnowned, _class: JClass, title: JString, directory: JString) -> *mut FFIClosure {
    enter_jni(env, |env| {
        let dir = if directory.is_null() { String::from("~") } else { directory.try_to_string(env)? };
        let mut dialog = AsyncFileDialog::new().set_directory(dir);
        if !title.is_null() {
            let title: String = title.try_to_string(env)?;
            dialog = dialog.set_title(title)
        }
        let fut = dialog.pick_folder();
        let f = Box::new(|| futures::executor::block_on(fut));
        let bo = Box::new(FFIClosure { f });
        Ok(Box::into_raw(bo))
    })
}
