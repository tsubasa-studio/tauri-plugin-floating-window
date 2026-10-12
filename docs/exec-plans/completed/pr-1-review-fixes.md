# PR #1 review fixes

Baseline: `feature_android` / `99bb465712d01202a4b7f657befdb5a5ee9658d5`.

Scope: fix all six review findings in plugin source, with regression tests;
keep example integration in a separate commit. Derived permissions/schemas must
only change through their generators. No merge or release is part of this task.

- Resolve exact bundled HTML assets before an extensionless SPA navigation fallback.
- Use native plugin channels for both click and close callbacks, with unregister support.
- Serialize the full cache check/extract/publish transaction and test cold-cache concurrency.
- Reject missing overlay permission before installing a window; resolve only after initialization.
- Apply dimensions and coordinates in documented physical pixels (retain density-scaled defaults).
- Accept host notification text in `create`, use neutral defaults and persist it for service recreation.

Validation: Rust cache regression tests; JS channel/cleanup tests and type/build checks;
Android unit tests and plugin compilation; build the example with both HTML entry points.
Device-only permission, visual geometry, notification and process lifecycle checks must be
reported separately from automated checks. Finish with an independent read-only review.

## Implementation completion — 2026-10-12

- Plugin fixes committed as `6dd6e3e`; example integration and test evidence as `0f3dbd3`.
- All six review findings addressed. No derived permissions/schemas changed.
- Passed: 3 Rust regression tests, 2 JS channel tests, 8 Kotlin unit tests,
  Android-target Rust check, TypeScript check, plugin JS/example frontend builds,
  Android plugin AAR and instrumentation test APK compilation.
- Independent read-only reviewer `review_fixes` reviewed the final source diff
  `99bb465..0f3dbd3`: no actionable blockers. Verified native event name conversion,
  lifecycle cleanup, geometry propagation, cache publication and notification config.
- This final documentation commit does not alter the reviewed implementation.
- Device acceptance remains pending (no connected Android device). Full example APK
  build is blocked by its pre-existing `com.tauri.dev` identifier. See the separate
  [validation record](../../../examples/tauri-app/REVIEW-REGRESSION.md) for commands,
  environment issues, exact limits and manual acceptance steps. No merge or release.
