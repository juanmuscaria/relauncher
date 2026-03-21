//! Simple native library to allow a "clean" relaunch of the JVM
//! - On POSIX compatible platforms it will use exec to replace the process entirely
//! - On Windows it will abuse `DllMain(DLL_PROCESS_DETACH)` to keep the current process alive
//!   with as little resource usage as possible while creating a new process with all handles
//!   properly configured.
//!
//! Declares all JNI functions present in `com.juanmuscaria.relauncher.NativeRelaunch`.

#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

use jni_sys::*;

// All libc symbols goes here
#[cfg(unix)]
unsafe extern "C" {
    fn execvp(
        file: *const std::os::raw::c_char,
        argv: *const *const std::os::raw::c_char,
    ) -> std::os::raw::c_int;
}

/// JNI: `NativeRelaunch.nativeExec(String[])`
///
/// Calls `execvp()` to replace the current process with the given command.
/// If successful, this function **never returns**. The JVM is gone.
/// If `execvp()` fails, returns normally so Java can fall back.
#[cfg(unix)]
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_juanmuscaria_relauncher_NativeRelaunch_nativeExec(
    env: *mut JNIEnv,
    _cls: jclass,
    args: jobjectArray,
) {
    unsafe {
        use std::ffi::{CStr, CString};
        use std::os::raw::c_char;

        let jni = &**env;

        let argc = (jni.v1_1.GetArrayLength)(env, args);
        if argc <= 0 {
            return;
        }

        let mut c_strings: Vec<CString> = Vec::with_capacity(argc as usize);
        for i in 0..argc {
            let jstr = (jni.v1_1.GetObjectArrayElement)(env, args, i) as jstring;
            let utf = (jni.v1_1.GetStringUTFChars)(env, jstr, std::ptr::null_mut());
            if utf.is_null() {
                return;
            }
            let owned = CString::from(CStr::from_ptr(utf));
            (jni.v1_1.ReleaseStringUTFChars)(env, jstr, utf);
            c_strings.push(owned);
        }

        // Build null-terminated argv for execvp
        let mut argv_ptrs: Vec<*const c_char> = c_strings.iter().map(|s| s.as_ptr()).collect();
        argv_ptrs.push(std::ptr::null());

        // https://man.archlinux.org/man/execvp.3.en
        execvp(argv_ptrs[0], argv_ptrs.as_ptr());
    }
}

#[cfg(windows)]
/// Module to hold all windows specific type sizes and symbols
mod win {
    use std::ffi::c_void;

    pub type LPVOID = *mut c_void;
    pub type HANDLE = *mut c_void;
    pub type BOOL = i32;
    pub type WORD = u16;
    pub type DWORD = u32;
    pub type WCHAR = u16;
    pub type LPWSTR = *mut WCHAR;
    pub type LPCWSTR = *const WCHAR;
    pub type LPBYTE = *mut u8;
    pub type LPDWORD = *mut DWORD;
    pub type LPHANDLE = *mut HANDLE;

    pub const TRUE: BOOL = 1;
    pub const DLL_PROCESS_DETACH: DWORD = 0;
    pub const INFINITE: DWORD = 0xFFFFFFFF;
    pub const STARTF_USESTDHANDLES: DWORD = 0x00000100;

    // https://learn.microsoft.com/en-us/windows/console/setstdhandle#parameters
    pub const STD_INPUT_HANDLE: DWORD = (-10_i32) as DWORD;
    pub const STD_OUTPUT_HANDLE: DWORD = (-11_i32) as DWORD;
    pub const STD_ERROR_HANDLE: DWORD = (-12_i32) as DWORD;

    pub const DUPLICATE_SAME_ACCESS: DWORD = 0x00000002;

    // https://learn.microsoft.com/pt-br/windows/win32/api/processthreadsapi/ns-processthreadsapi-startupinfow
    #[repr(C)]
    pub struct STARTUPINFOW {
        pub cb: DWORD,
        pub lp_reserved: LPWSTR,
        pub lp_desktop: LPWSTR,
        pub lp_title: LPWSTR,
        pub dw_x: DWORD,
        pub dw_y: DWORD,
        pub dw_x_size: DWORD,
        pub dw_y_size: DWORD,
        pub dw_x_count_chars: DWORD,
        pub dw_y_count_chars: DWORD,
        pub dw_fill_attribute: DWORD,
        pub dw_flags: DWORD,
        pub w_show_window: WORD,
        pub cb_reserved2: WORD,
        pub lp_reserved2: LPBYTE,
        pub h_std_input: HANDLE,
        pub h_std_output: HANDLE,
        pub h_std_error: HANDLE,
    }

    #[repr(C)]
    pub struct PROCESS_INFORMATION {
        pub h_process: HANDLE,
        pub h_thread: HANDLE,
        pub dw_process_id: DWORD,
        pub dw_thread_id: DWORD,
    }

    unsafe extern "system" {
        //https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw
        pub fn CreateProcessW(
            lp_application_name: LPCWSTR,
            lp_command_line: LPWSTR,
            lp_process_attributes: *const c_void, //LPSECURITY_ATTRIBUTES
            lp_thread_attributes: *const c_void,  //LPSECURITY_ATTRIBUTES
            b_inherit_handles: BOOL,
            dw_creation_flags: DWORD,
            lp_environment: LPVOID,
            lp_current_directory: LPCWSTR,
            lp_startup_info: *mut STARTUPINFOW, //LPSTARTUPINFOW
            lp_process_information: *mut PROCESS_INFORMATION, // LPPROCESS_INFORMATION
        ) -> BOOL;

        // https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-duplicatehandle
        pub fn DuplicateHandle(
            h_source_process_handle: HANDLE,
            h_source_handle: HANDLE,
            h_target_process_handle: HANDLE,
            lp_target_handle: LPHANDLE,
            dw_desired_access: DWORD,
            b_inherit_handle: BOOL,
            dw_options: DWORD,
        ) -> BOOL;
        pub fn GetCommandLineW() -> LPWSTR;
        pub fn WaitForSingleObject(h_handle: HANDLE, dw_milliseconds: DWORD) -> DWORD;
        pub fn GetExitCodeProcess(h_process: HANDLE, lp_exit_code: LPDWORD) -> BOOL;
        pub fn CloseHandle(h_object: HANDLE) -> BOOL;
        pub fn ExitProcess(u_exit_code: DWORD) -> !;
        pub fn GetCurrentProcess() -> HANDLE;
        pub fn GetStdHandle(n_std_handle: DWORD) -> HANDLE;
        pub fn lstrlenW(lp_string: LPCWSTR) -> i32;
    }
}

/// Command line, working directory, and standard handles, stored by
/// `nativeSetCommand` and consumed by `DllMain(DLL_PROCESS_DETACH)`.
///
/// Standard handles are captured during `nativeSetCommand` (before JVM shutdown)
/// because `exit()` may close them before `DllMain(DETACH)` fires.
#[cfg(windows)]
static mut COMMAND_LINE: *mut u16 = std::ptr::null_mut();
#[cfg(windows)]
static mut WORKING_DIR: *mut u16 = std::ptr::null_mut();
#[cfg(windows)]
static mut SAVED_STDIN: win::HANDLE = std::ptr::null_mut();
#[cfg(windows)]
static mut SAVED_STDOUT: win::HANDLE = std::ptr::null_mut();
#[cfg(windows)]
static mut SAVED_STDERR: win::HANDLE = std::ptr::null_mut();

/// Converts a Java string (UTF-16) to a heap-allocated null-terminated wide
/// string. The allocation is intentionally leaked (never freed) since it
/// must survive until `DLL_PROCESS_DETACH` when the process is terminating.
#[cfg(windows)]
unsafe fn jstring_to_wide(env: *mut JNIEnv, s: jstring) -> *mut u16 {
    unsafe {
        if s.is_null() {
            return std::ptr::null_mut();
        }

        let jni = &**env;
        let len = (jni.v1_1.GetStringLength)(env, s) as usize;
        let chars = (jni.v1_1.GetStringChars)(env, s, std::ptr::null_mut());
        if chars.is_null() {
            return std::ptr::null_mut();
        }

        let mut buf: Vec<u16> = Vec::with_capacity(len + 1);
        std::ptr::copy_nonoverlapping(chars, buf.as_mut_ptr(), len);
        buf.set_len(len);
        buf.push(0);

        (jni.v1_1.ReleaseStringChars)(env, s, chars);

        let ptr = buf.as_mut_ptr();
        std::mem::forget(buf);
        ptr
    }
}

/// Duplicates a handle into `out`, making it inheritable. Falls back to
/// storing the original if `DuplicateHandle` fails.
#[cfg(windows)]
unsafe fn dup_handle(process: win::HANDLE, src: win::HANDLE, out: *mut win::HANDLE) {
    unsafe {
        let ok = win::DuplicateHandle(
            process,
            src,
            process,
            out,
            0,
            win::TRUE, // bInheritHandle = TRUE
            win::DUPLICATE_SAME_ACCESS,
        );
        if ok == 0 {
            out.write(src);
        }
    }
}

#[cfg(windows)]
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_juanmuscaria_relauncher_NativeRelaunch_nativeSetCommand(
    env: *mut JNIEnv,
    _cls: jclass,
    command_line: jstring,
    working_dir: jstring,
) -> jboolean {
    unsafe {
        COMMAND_LINE = jstring_to_wide(env, command_line);
        WORKING_DIR = jstring_to_wide(env, working_dir);

        let cur = win::GetCurrentProcess();
        dup_handle(
            cur,
            win::GetStdHandle(win::STD_INPUT_HANDLE),
            &raw mut SAVED_STDIN,
        );
        dup_handle(
            cur,
            win::GetStdHandle(win::STD_OUTPUT_HANDLE),
            &raw mut SAVED_STDOUT,
        );
        dup_handle(
            cur,
            win::GetStdHandle(win::STD_ERROR_HANDLE),
            &raw mut SAVED_STDERR,
        );

        if COMMAND_LINE.is_null() {
            JNI_FALSE
        } else {
            JNI_TRUE
        }
    }
}

/// JNI: `NativeRelaunch.nativeGetCommandLine()`
///
/// Returns the original command line from `GetCommandLineW()`. This is the
/// exact string the OS used to create this process, unmodified by any Java
/// bootstrap code that may have changed `java.class.path`.
#[cfg(windows)]
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_juanmuscaria_relauncher_NativeRelaunch_nativeGetCommandLine(
    env: *mut JNIEnv,
    _cls: jclass,
) -> jstring {
    unsafe {
        let jni = &**env;
        let cmd = win::GetCommandLineW();
        if cmd.is_null() {
            return std::ptr::null_mut();
        }

        (jni.v1_1.NewString)(env, cmd, win::lstrlenW(cmd))
    }
}

/// DLL entry point. During `DLL_PROCESS_DETACH` (process termination),
/// creates the child process and waits for it, then propagates its exit code.
///
/// At this point the JVM is hopefully torn down with no heap, no threads, no GC.
/// Only windows calls are safe.
///
/// Dll trick provided by a friend.
// https://learn.microsoft.com/en-us/windows/win32/dlls/dllmain
// 	::BOOL APIENTRY DllMain(::HMODULE const, ::DWORD const reason, ::LPVOID const reserved) noexcept
#[cfg(windows)]
#[unsafe(no_mangle)]
pub unsafe extern "system" fn DllMain(
    _hinst_dll: *mut std::ffi::c_void, // HMODULE
    fdw_reason: win::DWORD,
    lpv_reserved: win::LPVOID,
) -> i32 {
    unsafe {
        use win::*;

        if fdw_reason != DLL_PROCESS_DETACH || lpv_reserved.is_null() {
            // not process shutdown, nothing to do
            return TRUE;
        }

        if COMMAND_LINE.is_null() {
            panic!("Relauncher not armed properly!")
        }

        let mut si: STARTUPINFOW = std::mem::zeroed();
        si.cb = size_of::<STARTUPINFOW>() as DWORD;
        si.dw_flags = STARTF_USESTDHANDLES;
        si.h_std_input = SAVED_STDIN;
        si.h_std_output = SAVED_STDOUT;
        si.h_std_error = SAVED_STDERR;

        let mut pi: PROCESS_INFORMATION = std::mem::zeroed();

        let ok = CreateProcessW(
            std::ptr::null(),
            COMMAND_LINE,
            std::ptr::null(),
            std::ptr::null(),
            TRUE,
            0,
            std::ptr::null_mut(),
            WORKING_DIR,
            &mut si,
            &mut pi,
        );

        if ok != 0 {
            CloseHandle(pi.h_thread);
            WaitForSingleObject(pi.h_process, INFINITE);

            let mut exit_code: DWORD = 1;
            GetExitCodeProcess(pi.h_process, &mut exit_code);

            ExitProcess(exit_code);
        }

        TRUE
    }
}
