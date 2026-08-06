use tauri::{
  plugin::{Builder, TauriPlugin},
  Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::FloatingWindow;
#[cfg(mobile)]
use mobile::FloatingWindow;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the floating-window APIs.
pub trait FloatingWindowExt<R: Runtime> {
  fn floating_window(&self) -> &FloatingWindow<R>;
}

impl<R: Runtime, T: Manager<R>> crate::FloatingWindowExt<R> for T {
  fn floating_window(&self) -> &FloatingWindow<R> {
    self.state::<FloatingWindow<R>>().inner()
  }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
  Builder::new("floating-window")
    .invoke_handler(tauri::generate_handler![commands::ping])
    .setup(|app, api| {
      #[cfg(mobile)]
      let floating_window = mobile::init(app, api)?;
      #[cfg(desktop)]
      let floating_window = desktop::init(app, api)?;
      app.manage(floating_window);
      Ok(())
    })
    .build()
}
