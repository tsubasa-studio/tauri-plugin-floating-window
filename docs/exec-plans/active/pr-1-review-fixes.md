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
