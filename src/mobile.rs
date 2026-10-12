use serde::de::DeserializeOwned;
use tauri::{
  plugin::{PluginApi, PluginHandle},
  AppHandle, Runtime,
};

use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_floating_window);

// initializes the Kotlin or Swift plugin classes
pub fn init<R: Runtime, C: DeserializeOwned>(
  _app: &AppHandle<R>,
  api: PluginApi<R, C>,
) -> crate::Result<FloatingWindow<R>> {
  #[cfg(target_os = "android")]
  let handle = api.register_android_plugin("com.plugin.floatingwindow", "FloatingWindowPlugin")?;
  #[cfg(target_os = "ios")]
  let handle = api.register_ios_plugin(init_plugin_floating_window)?;
  Ok(FloatingWindow(handle))
}

/// Access to the floating-window APIs.
pub struct FloatingWindow<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> FloatingWindow<R> {
  pub fn check_permission(&self) -> crate::Result<PermissionResponse> {
    self.0.run_mobile_plugin("check_permission", ()).map_err(Into::into)
  }

  pub fn request_permission(&self) -> crate::Result<PermissionResponse> {
    self.0.run_mobile_plugin("request_permission", ()).map_err(Into::into)
  }

  pub fn create(&self, payload: CreateConfig) -> crate::Result<CreateResponse> {
    self.0.run_mobile_plugin("create", payload).map_err(Into::into)
  }

  pub fn show(&self, payload: TagRequest) -> crate::Result<()> {
    self.0.run_mobile_plugin("show", payload).map_err(Into::into)
  }

  pub fn hide(&self, payload: TagRequest) -> crate::Result<()> {
    self.0.run_mobile_plugin("hide", payload).map_err(Into::into)
  }

  pub fn move_window(&self, payload: MoveConfig) -> crate::Result<()> {
    self.0.run_mobile_plugin("move_window", payload).map_err(Into::into)
  }

  pub fn destroy(&self, payload: TagRequest) -> crate::Result<()> {
    self.0.run_mobile_plugin("destroy", payload).map_err(Into::into)
  }
}
