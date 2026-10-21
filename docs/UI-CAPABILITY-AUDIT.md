# UI capability audit and implementation notes

Audit baseline: `main` at `c246639c96d1a9191f613c96e86c46a88bbfba8c` plus UI commits `49645b4` and `6352cc9`. The repository currently uses Kotlin DSL, AGP via CI, Gradle 8.9, JDK 17, compile/target SDK 35, min SDK 26, Compose BOM `2024.10.01`, Material 3 from that BOM, Room `2.6.1`, and Activity Compose `1.9.3`. There is no version catalog.

## Implemented in this change

- Lightweight spring-based `animateContentSize` on the shared brand/glass panels. This avoids per-frame blur and keeps the existing visual design and data flow unchanged.
- Subtle Compose haptic feedback when the user changes the main navigation section. It is intentionally limited to an explicit user action and does not run during scrolling or financial updates.
- Existing RTL provider, dark/light Material 3 theme, Jalali date formatting, Toman formatting, Room persistence, and custom Canvas charts remain in place.

## Compatibility decisions for the 21 requested areas

| # | Capability | Decision for this baseline |
|---|---|---|
| 1 | Material 3 Expressive | Do not blindly upgrade the BOM. Current Material 3 is BOM-managed and older; first migrate Compose BOM as a coordinated tested change. |
| 2 | Predictive Back | Keep platform back behavior for now; validate Android 13+ gesture previews after checking Activity/Compose versions and real navigation stack. |
| 3 | DeferredAnimatedContent | No dependency or source API with this exact name is currently used. Treat as a concept/name requiring verification, not a drop-in API. |
| 4 | DeferredAnimatedVisibility | Same: do not import an unverified API name. Stable `AnimatedVisibility` exists in Compose Animation. |
| 5 | DeferredTargetAnimation | Exact API/version needs verification; do not introduce a potentially unavailable API. |
| 6 | Shared Element Transitions | Requires a compatible newer Compose Animation API and navigation/state wiring. Defer until customer/invoice detail destinations have explicit stable IDs and tests. |
| 7 | Compose Animation APIs | Started: spring-based content-size transitions. Further transitions should be limited to navigation and meaningful state changes. |
| 8 | Adaptive Layouts | Current UI is a single-column mobile-first LazyColumn. Window-size-class support needs a separate layout/test pass; not added just to create an untested second layout. |
| 9 | Material 3 Design System | Existing Material 3 theme has light/dark color schemes, semantic colors, shapes, and RTL direction. |
| 10 | Vico Charts | Current project uses custom Canvas charts and has no Vico dependency. Keep dependency-free charts for now; evaluate Vico license, stable version, offline artifact availability, accessibility, and migration cost before adding. |
| 11 | Android Haptics | Started: subtle haptic on explicit top-level section selection; no financial operation is triggered by haptics. |
| 12 | Compose Runtime Optimization | Avoided adding backdrop blur/heavy effects. Full recomposition/performance profiling still required. |
| 13 | Macrobenchmark | Not configured. Add only with a dedicated benchmark module and CI/device baseline; not proven by unit tests. |
| 14 | Baseline Profiles | Not configured. Needs benchmark/profile generation and installation-path validation before adoption. |
| 15 | Compose UI Testing | Instrumented UI tests already exist and passed in CI #208 (14/14); new animation/haptic changes must be checked in a new run. |
| 16 | Room Database | Already used with local encrypted database integration; no change to schemas or accounting writes in this UI pass. |
| 17 | WorkManager | Not currently a dependency. Consider only for genuinely persistent background tasks; local debt reminders require notification permission/channel and schedule tests. |
| 18 | Google AI Edge / LiteRT | Not currently integrated. Keep insights offline and deterministic until a model, model license, APK size, RAM and device performance are validated. |
| 19 | Accessibility | Existing Material components provide baseline semantics; run TalkBack, font scaling, contrast, and touch-target checks before claiming full compliance. |
| 20 | Android Architecture Guide | Existing code separates data/domain/UI in packages; architecture modularization is incomplete and should be incremental, preserving financial transaction boundaries. |
| 21 | A2UI | Do not add to the offline core: agent-generated UI introduces a runtime/protocol and validation surface. If explored later, allowlist components and keep all accounting mutations behind typed local domain actions. |

## Free/open-source and source checks

- AndroidX/Jetpack libraries are published by Google under AndroidX/Jetpack licensing; use the official Android developer docs and Maven artifacts.
- Vico project source: https://github.com/patrykandpatrick/vico — inspect the exact release tag and LICENSE file before selecting a version; do not infer that every hosted service or optional integration is free.
- A2UI source: https://github.com/google/A2UI — inspect the repository LICENSE and runtime requirements before any adoption; it is not needed for a fully offline accounting UI.
- This repository currently has no Vico, WorkManager, LiteRT, Macrobenchmark, or Baseline Profile dependency. No extra download or runtime dependency was introduced by the two UI improvements in this pass.

## Acceptance still required

A passing compile alone is not enough. Run CI against the new commit; inspect screenshots for dashboard, customers, inventory, invoice, and reports; test Persian RTL and Jalali/Toman display, TalkBack/font scaling, repeated navigation, and performance on the target Redmi Note 13. The CI emulator is not the physical target device. Do not mark release-ready until these checks and the signing/release process are complete.
