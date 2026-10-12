# PR #1 event follow-up

Baseline: `545b52dd11710039bf722ca9925d3705f41f38fb`, `feature_android`.

The second review found two gaps in the event path: Tauri blocks native listener
commands before Android dispatch, and FloatingX's parent click listener is bypassed
when the child WebView consumes a tap.

- Add listener commands to the plugin permission generator and default permission set.
- Observe taps on the WebView without consuming its touches or disabling FloatingX drag.
- Verify actual Tauri ACL resolution with the example capability, including rejection
  without plugin permission and for unrelated windows/origins.
- Exercise Android GestureDetector and FloatingX parent/child dispatch with taps,
  drag cancellation, and page touch delivery; retain device checks for real HTML buttons.
- Generate permissions and application schemas through the existing Cargo build scripts.
- Commit plugin implementation/tests, example validation, and generated outputs separately.
- Run independent read-only review before pushing. No merge or release.

Implemented both fixes and generated the permissions/schemas through the existing
Cargo build scripts. Validation: 12 Android JVM tests, 2 actual example ACL tests,
and 2 JS channel tests passed. Android Rust check, plugin AAR and instrumentation
APK compilation passed. See `examples/tauri-app/REVIEW-REGRESSION.md` for commands,
coverage boundaries and the outstanding device checks (no device connected).

Commits: `2e8bf32` implementation/Android tests, `1414ea6` example ACL tests and
validation record, `7b09825` generated artifacts. Independent read-only review of
`545b52d..7b0982585735172c8110c20324bc5751fbefe60e` found no actionable blockers.
The final committed Kotlin sources passed the combined JVM/AAR/instrumentation
build again (12 JVM tests). Real WebView instrumentation remains unexecuted, and
full plugin IPC, system-overlay interaction and disposal still need device
acceptance. No merge, install, or release was performed.
