use tauri::{AppHandle, command, Runtime};

use crate::models::*;
use crate::Result;
use crate::FloatingWindowExt;

#[command]
pub(crate) async fn check_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<PermissionResponse> {
    app.floating_window().check_permission()
}

#[command]
pub(crate) async fn request_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<PermissionResponse> {
    app.floating_window().request_permission()
}

#[command]
pub(crate) async fn create<R: Runtime>(
    app: AppHandle<R>,
    payload: CreateConfig,
) -> Result<CreateResponse> {
    app.floating_window().create(payload)
}

#[command]
pub(crate) async fn show<R: Runtime>(
    app: AppHandle<R>,
    payload: TagRequest,
) -> Result<()> {
    app.floating_window().show(payload)
}

#[command]
pub(crate) async fn hide<R: Runtime>(
    app: AppHandle<R>,
    payload: TagRequest,
) -> Result<()> {
    app.floating_window().hide(payload)
}

#[command]
pub(crate) async fn move_window<R: Runtime>(
    app: AppHandle<R>,
    payload: MoveConfig,
) -> Result<()> {
    app.floating_window().move_window(payload)
}

#[command]
pub(crate) async fn destroy<R: Runtime>(
    app: AppHandle<R>,
    payload: TagRequest,
) -> Result<()> {
    app.floating_window().destroy(payload)
}
