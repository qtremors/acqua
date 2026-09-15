# Acqua - Tasks

> **Project:** Acqua
>
> **Version:** 0.3.0
>
> **Last Updated:** 2026-09-15

---

## Remaining Work

- [ ] **TEST-0002 - Complete End-To-End UI, Accessibility, And State-Restoration Coverage** `[High]` `[Partially Complete]`
  - **Location:** `acqua-app/app/src/androidTest` `acqua-app/feature/browser/src/androidTest` `acqua-app/feature/downloads/src/androidTest` `acqua-app/core/data/src/androidTest`
  - **Completed:** Module-owned instrumentation sources cover About and History Compose behavior, browser ViewModel behavior, and device-dependent history, settings, session, resolver, and storage integrations. Downloader, Settings, Updates, and Onboarding restoration/orchestration are covered by deterministic JVM tests. Every instrumentation APK compiles successfully with `assembleDebugAndroidTest`.
  - **Remaining implementation:** Add hermetic test hosts and fakes for dashboard navigation, downloader loading/error/progress/completion states, permission denial and recovery, browser-to-resolver handoff, and History actions. Add screenshot and accessibility assertions for semantic labels, focus order, live progress, keyboard operation, adaptive layouts, large fonts, and RTL.
  - **Remaining verification:** Run the connected suite with external networking disabled across light/dark themes, RTL, 1.0x/1.5x/2.0x fonts, compact/expanded windows, rotation, activity recreation, and process-death restoration. Exercise the supported API/device matrix, including API 24, 29, 33, 35, and the current target API.
  - **Current blocker:** No Android device or emulator is attached to this workspace, so connected runtime and accessibility verification cannot be completed here.
  - **Completion criteria:** All scenarios above pass without external network access, one-off events are not replayed after recreation, active work is restored from durable state, and accessibility checks report no actionable failures.

---

## Verification Baseline

- `verifyLocalRelease`: passed on 2026-09-15 with all 637 tasks in the graph.
- `assembleDebugAndroidTest`: passed for every Android module.
- JVM tests, architecture rules, Kotlin quality checks, debug/release lint, R8 minification, release artifact checks, and static-site validation are green.
- Completed tasks have been removed from this file.
