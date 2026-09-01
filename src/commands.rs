#[cfg(target_os = "android")]
use std::{
    collections::hash_map::DefaultHasher,
    fs,
    hash::{Hash, Hasher},
    io,
    path::{Component, Path, PathBuf},
};

#[cfg(target_os = "android")]
use tauri::Manager;
use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::FloatingWindowExt;
use crate::Result;

#[cfg(target_os = "android")]
const ANDROID_ASSET_CACHE_FORMAT_VERSION: u8 = 2;

#[command]
pub(crate) async fn check_permission<R: Runtime>(app: AppHandle<R>) -> Result<PermissionResponse> {
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
    mut payload: CreateConfig,
) -> Result<CreateResponse> {
    if tauri::is_dev() {
        payload.dev_server_url = app.config().build.dev_url.as_ref().map(ToString::to_string);
    }

    #[cfg(target_os = "android")]
    if !tauri::is_dev() {
        let asset_app = app.clone();
        let asset_root =
            tauri::async_runtime::spawn_blocking(move || prepare_android_assets(&asset_app))
                .await
                .map_err(|error| {
                    io::Error::other(format!("asset extraction task failed: {error}"))
                })??;
        payload.asset_root = Some(asset_root.to_string_lossy().into_owned());
    }

    app.floating_window().create(payload)
}

#[cfg(target_os = "android")]
fn prepare_android_assets<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf> {
    let resolver = app.asset_resolver();
    let mut hasher = DefaultHasher::new();
    ANDROID_ASSET_CACHE_FORMAT_VERSION.hash(&mut hasher);
    for (path, bytes) in resolver.iter() {
        path.hash(&mut hasher);
        bytes.as_ref().hash(&mut hasher);
    }

    let fingerprint = format!("{:016x}", hasher.finish());
    let base_dir = app
        .path()
        .app_cache_dir()
        .map_err(|error| io::Error::other(format!("failed to resolve app cache dir: {error}")))?
        .join("floating-window-assets");
    let asset_root = base_dir.join(&fingerprint);
    let completion_marker = asset_root.join(".complete");
    if completion_marker.is_file() {
        return Ok(asset_root);
    }

    fs::create_dir_all(&base_dir)?;
    let temporary_root = base_dir.join(format!(".{fingerprint}.tmp-{}", std::process::id()));
    if temporary_root.exists() {
        fs::remove_dir_all(&temporary_root)?;
    }
    fs::create_dir_all(&temporary_root)?;

    let extraction_result = (|| -> Result<()> {
        for (path, _) in resolver.iter() {
            let destination = safe_asset_path(&temporary_root, path.as_ref())?;
            if let Some(parent) = destination.parent() {
                fs::create_dir_all(parent)?;
            }
            let asset = resolver.get(path.to_string()).ok_or_else(|| {
                io::Error::new(
                    io::ErrorKind::NotFound,
                    format!("failed to resolve bundled asset: {path}"),
                )
            })?;
            fs::write(destination, asset.bytes)?;
        }
        fs::write(temporary_root.join(".complete"), fingerprint.as_bytes())?;
        Ok(())
    })();

    if let Err(error) = extraction_result {
        let _ = fs::remove_dir_all(&temporary_root);
        return Err(error);
    }

    if asset_root.exists() {
        fs::remove_dir_all(&asset_root)?;
    }
    fs::rename(&temporary_root, &asset_root)?;
    Ok(asset_root)
}

#[cfg(target_os = "android")]
fn safe_asset_path(root: &Path, asset_path: &str) -> io::Result<PathBuf> {
    let mut destination = root.to_path_buf();
    for component in Path::new(asset_path).components() {
        match component {
            Component::Normal(segment) => destination.push(segment),
            Component::CurDir | Component::RootDir => {}
            Component::ParentDir | Component::Prefix(_) => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    format!("invalid bundled asset path: {asset_path}"),
                ));
            }
        }
    }

    if destination == root {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "bundled asset path is empty",
        ));
    }
    Ok(destination)
}

#[command]
pub(crate) async fn show<R: Runtime>(app: AppHandle<R>, payload: TagRequest) -> Result<()> {
    app.floating_window().show(payload)
}

#[command]
pub(crate) async fn hide<R: Runtime>(app: AppHandle<R>, payload: TagRequest) -> Result<()> {
    app.floating_window().hide(payload)
}

#[command]
pub(crate) async fn move_window<R: Runtime>(app: AppHandle<R>, payload: MoveConfig) -> Result<()> {
    app.floating_window().move_window(payload)
}

#[command]
pub(crate) async fn destroy<R: Runtime>(app: AppHandle<R>, payload: TagRequest) -> Result<()> {
    app.floating_window().destroy(payload)
}
