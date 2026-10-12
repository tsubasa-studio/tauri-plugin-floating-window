# PR #1 Android regression checks

Use a packaged APK with the Vite dev server stopped. A successful frontend build
alone does not verify WebView behavior on a device.

1. Revoke overlay permission. Press either Create button: it must reject without
   opening settings, installing a blank overlay, or starting a service. Press
   Request Permission, grant it in Settings, return, then press Create again.
2. The overlay must display the independent `floating.html` bubble and Close
   button, not the main controls from `index.html`. The JVM test also resolves
   both real files in the built `dist` directory through the release resolver.
3. Compare the two Create presets: 140 × 140 at (120, 120), and 240 × 180 at
   (20, 350), in physical pixels. Coordinates are within FloatingX's work area;
   Android system insets and screen boundaries can constrain final placement.
4. Tap an overlay: the main log must show its tag. Close it from its page: the log
   must show `closed` with the same tag. Reopening the main view must not accumulate
   duplicate event callbacks (both subscriptions are removed on unmount).
5. On a fresh installation or after clearing this example app's cache, press
   Create both concurrently. Both tags must succeed and load the floating page.
6. Leave Custom notification text unchecked: title uses the host application
   label and the channel/body use neutral floating-window wording. Enable it and
   create again: check all four strings against the example's `notification`
   object. The latest create supplies app-wide text for all windows.
7. Exercise service recreation in an Android lifecycle test harness: after saving
   custom configuration, stop/recreate the service without calling `create` again.
   Its `onCreate` must show the stored strings. Recreate a window with the checkbox
   off to reset all fields to defaults. The service remains `START_NOT_STICKY`;
   this change does not add automatic overlay restoration after process death.

## Automated commands

From the plugin root:

```powershell
bun test tests/events.test.ts
bun run build
bun run --cwd examples/tauri-app build
cargo test --lib
cargo check --target aarch64-linux-android
```

From `examples/tauri-app/src-tauri/gen/android`, with JDK 17 or 21:

```powershell
.\gradlew.bat :tauri-plugin-floating-window:testDebugUnitTest :tauri-plugin-floating-window:assembleDebug
# Requires a device; verifies persistence through fresh Android contexts:
.\gradlew.bat :tauri-plugin-floating-window:connectedDebugAndroidTest
```

The plugin JVM tests cover exact HTML lookup, limited SPA fallback, missing assets,
path traversal, geometry, and default/custom notification text. Rust tests cover
concurrent cold-cache publication, retry after failed extraction, and safe paths.
JS tests use Tauri's real Channel API with a mocked native IPC boundary.

## Validation record — 2026-10-12

Plugin implementation: `6dd6e3e`; example integration is committed separately.
Windows, Bun 1.3.14, Cargo/rustc 1.97.1, JDK 21.0.11, Gradle 8.14.3.

| Check | Result |
| --- | --- |
| `bun test tests/events.test.ts` (plugin root) | Exit 0; 2 passed |
| `bun run build` (plugin root) | Exit 0; Rollup ESM/CJS/declarations built |
| `bun run --cwd examples/tauri-app build` (plugin root) | Exit 0; Vite built both HTML entry points |
| `bun ../../node_modules/typescript/bin/tsc --noEmit` (plugin root, workspace compiler) | Exit 0 |
| `rustc --edition=2021 --test src/asset_cache.rs -o target/asset-cache-tests.exe`, then run executable (plugin root) | Exit 0; 3 passed against the production module, not a copied implementation |
| `cargo check --offline --target aarch64-linux-android` (plugin root) | Exit 0 |
| `gradlew.bat :tauri-plugin-floating-window:testDebugUnitTest :tauri-plugin-floating-window:assembleDebug` (generated Android project) | Exit 0; 8 passed; plugin AAR built |
| `gradlew.bat :tauri-plugin-floating-window:testDebugUnitTest :tauri-plugin-floating-window:assembleDebugAndroidTest --console=plain '-Pkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false'` (same directory) | Exit 0; 8 passed; notification persistence test APK compiled |
| `git -c core.whitespace=cr-at-eol diff --check` | Exit 0; existing CRLF files retained |

Not executed: instrumentation on a device, permission/settings return flow, actual
window placement, native click/close delivery, and service recreation display.
`adb devices` returned no connected devices. The instrumentation APK compiles but
its compilation is not evidence that persistence/lifecycle tests ran on Android.

Full example APK attempt: `bun run tauri android build --debug --target aarch64 --apk --ci`
exited 1 before compilation because the existing example identifier `com.tauri.dev`
is forbidden by the CLI. Changing the application ID also requires updating its
initialized native project; this is separate from the six plugin fixes.

Initial environment issues were resolved for plugin checks: the default JDK 25
is unsupported by this Gradle version, so JDK 21 was selected; missing pinned
Kotlin dependencies were downloaded. The final in-process compiler invocation
avoids the machine's Kotlin daemon incremental-cache failure. Existing dependency
deprecation warnings and the missing `consumer-rules.pro` warning remain.

No generated permission or schema changes were needed. No APK was installed or
published. Revert `0f3dbd3` and then `6dd6e3e` to undo the example and implementation.

Independent read-only review of `99bb465..0f3dbd3` found no actionable blockers in
the six fixes. The reviewer explicitly retained the device-only limitations above.
