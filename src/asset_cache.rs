use std::{
    fs, io,
    path::{Component, Path, PathBuf},
    sync::Mutex,
};

// All create() tasks in this process share the same cache and temporary path.
// Hold the lock from the completion check through publication, including cleanup.
static ASSET_CACHE_LOCK: Mutex<()> = Mutex::new(());

pub(crate) fn prepare_cache(
    base_dir: &Path,
    fingerprint: &str,
    extract: impl FnOnce(&Path) -> io::Result<()>,
) -> io::Result<PathBuf> {
    let _guard = ASSET_CACHE_LOCK
        .lock()
        .map_err(|_| io::Error::other("asset cache lock poisoned"))?;
    let asset_root = base_dir.join(fingerprint);
    if asset_root.join(".complete").is_file() {
        return Ok(asset_root);
    }

    fs::create_dir_all(base_dir)?;
    let temporary_root = base_dir.join(format!(".{fingerprint}.tmp-{}", std::process::id()));
    if temporary_root.exists() {
        fs::remove_dir_all(&temporary_root)?;
    }
    fs::create_dir_all(&temporary_root)?;

    let result = (|| {
        extract(&temporary_root)?;
        fs::write(temporary_root.join(".complete"), fingerprint.as_bytes())?;
        if asset_root.exists() {
            fs::remove_dir_all(&asset_root)?;
        }
        fs::rename(&temporary_root, &asset_root)
    })();
    if result.is_err() {
        let _ = fs::remove_dir_all(&temporary_root);
    }
    result?;
    Ok(asset_root)
}

pub(crate) fn safe_asset_path(root: &Path, asset_path: &str) -> io::Result<PathBuf> {
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{
        atomic::{AtomicUsize, Ordering},
        mpsc, Arc,
    };
    use std::time::Duration;

    static NEXT_ID: AtomicUsize = AtomicUsize::new(0);

    struct TestDir(PathBuf);
    impl TestDir {
        fn new() -> Self {
            Self(std::env::temp_dir().join(format!(
                "floating-cache-test-{}-{}",
                std::process::id(),
                NEXT_ID.fetch_add(1, Ordering::SeqCst)
            )))
        }
    }
    impl Drop for TestDir {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn concurrent_cold_cache_creates_publish_once() {
        let dir = TestDir::new();
        let calls = Arc::new(AtomicUsize::new(0));
        let (started_tx, started_rx) = mpsc::channel();
        let (release_tx, release_rx) = mpsc::channel();
        let (done_tx, done_rx) = mpsc::channel();
        std::thread::scope(|scope| {
            let first_dir = &dir.0;
            let first_calls = Arc::clone(&calls);
            let first = scope.spawn(move || {
                prepare_cache(first_dir, "bundle", |root| {
                    first_calls.fetch_add(1, Ordering::SeqCst);
                    fs::write(root.join("index.html"), "main")?;
                    started_tx.send(()).unwrap();
                    release_rx.recv_timeout(Duration::from_secs(5)).unwrap();
                    fs::write(root.join("floating.html"), "floating")
                })
            });
            started_rx.recv_timeout(Duration::from_secs(5)).unwrap();
            let second = scope.spawn(|| {
                let result = prepare_cache(&dir.0, "bundle", |_| {
                    calls.fetch_add(1, Ordering::SeqCst);
                    panic!("second tag must reuse the published cache")
                });
                done_tx.send(()).unwrap();
                result
            });
            let blocked = done_rx.recv_timeout(Duration::from_millis(100)).is_err();
            release_tx.send(()).unwrap();
            let a = first.join().unwrap().unwrap();
            let b = second.join().unwrap().unwrap();
            assert!(blocked);
            assert_eq!(a, b);
            assert_eq!(calls.load(Ordering::SeqCst), 1);
            assert_eq!(
                fs::read_to_string(b.join("floating.html")).unwrap(),
                "floating"
            );
            assert_eq!(fs::read_to_string(b.join("index.html")).unwrap(), "main");
            assert!(b.join(".complete").is_file());
        });
    }

    #[test]
    fn failed_extraction_can_be_retried() {
        let dir = TestDir::new();
        assert!(prepare_cache(&dir.0, "bundle", |root| {
            fs::write(root.join("partial"), "incomplete")?;
            Err(io::Error::other("simulated failure"))
        })
        .is_err());
        let root = prepare_cache(&dir.0, "bundle", |root| {
            fs::write(root.join("index.html"), "ok")
        })
        .unwrap();
        assert!(!root.join("partial").exists());
        assert!(root.join(".complete").is_file());
    }

    #[test]
    fn asset_paths_cannot_escape_cache() {
        let root = Path::new("cache");
        assert!(safe_asset_path(root, "../secret").is_err());
        assert!(safe_asset_path(root, "").is_err());
        assert_eq!(
            safe_asset_path(root, "/assets/main.js").unwrap(),
            root.join("assets/main.js")
        );
    }
}
