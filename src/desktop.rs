use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<FloatingWindow<R>> {
    Ok(FloatingWindow(app.clone()))
}

/// Access to the floating-window APIs.
/// Desktop implementation returns errors — floating windows are mobile-only.
pub struct FloatingWindow<R: Runtime>(AppHandle<R>);

impl<R: Runtime> FloatingWindow<R> {
    pub fn check_permission(&self) -> crate::Result<PermissionResponse> {
        Ok(PermissionResponse { granted: true })
    }

    pub fn request_permission(&self) -> crate::Result<PermissionResponse> {
        Ok(PermissionResponse { granted: true })
    }

    pub fn create(&self, _payload: CreateConfig) -> crate::Result<CreateResponse> {
        Err(crate::Error::DesktopNotSupported)
    }

    pub fn show(&self, _payload: TagRequest) -> crate::Result<()> {
        Err(crate::Error::DesktopNotSupported)
    }

    pub fn hide(&self, _payload: TagRequest) -> crate::Result<()> {
        Err(crate::Error::DesktopNotSupported)
    }

    pub fn move_window(&self, _payload: MoveConfig) -> crate::Result<()> {
        Err(crate::Error::DesktopNotSupported)
    }

    pub fn destroy(&self, _payload: TagRequest) -> crate::Result<()> {
        Err(crate::Error::DesktopNotSupported)
    }
}
