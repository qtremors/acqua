# Acqua - Tasks

> **Project:** Acqua
>
> **Version:** 0.2.4
>
> **Last Updated:** 2026-09-13

---

### Architecture / Maintainability Tasks

- [ ] **ARCH-0001 - Modularize Web Resolver DOM Extraction Script** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/resolver/web/RenderedPageResolverActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/auth/LivePageMediaCollector.kt`
  - **Problem:** `RenderedPageResolverActivity` embeds a 130-line JavaScript string containing ad-hoc DOM queries, JSON serialization, and full-document `innerHTML` evaluation (`document.documentElement.innerHTML`) on every pass. This mixes raw JavaScript strings inside Kotlin, causes massive string allocations in WebView memory on every 250ms polling interval, and duplicates logic found in `LivePageMediaCollector`.
  - **Impact:** Memory churn and potential GC pauses during media resolution; high maintenance overhead and brittle updates when web targets alter their DOM hierarchy.
  - **Fix:** Extract the web injection script into a standalone, testable `.js` asset; replace full-page `innerHTML` regex scans with targeted DOM element checks; and decouple DOM parsing from the Android Activity lifecycle into a modular web extraction engine.
  - **Verification:** Run unit and instrumentation tests for `RenderedPageResolverActivity` across large media pages; profile heap memory allocations in Android Studio profiler to confirm absence of large repeated `innerHTML` string copies.

- [x] **ARCH-0002 - Add Enforced Architecture Boundary Tests** `[High]`
  - **Location:** `acqua-app/app/src/test/java/dev/qtremors/acqua/ArchitectureBoundaryTest.kt` `acqua-app/app/build.gradle.kts` `acqua-app/gradle/libs.versions.toml` `acqua-app/settings.gradle.kts`
  - **Problem:** Acqua describes package-level clean MVVM boundaries, but no automated rule prevents domain code from gaining Android dependencies, shared UI from importing feature/data implementations, features from importing unrelated features, ViewModels from owning platform details, or module dependencies from reversing direction. The single `:app` module currently allows every production class to reach every other class.
  - **Impact:** Architectural regressions compile successfully, feature independence declines silently, and later module extraction becomes an expensive graph rewrite instead of a mechanical move.
  - **Fix:** Add an ArchUnit-backed architecture test plus focused source checks for platform-neutral domain code, feature independence, shared-UI neutrality, approved feature public APIs, package/path ownership, ViewModel platform restrictions, composable infrastructure restrictions, and allowed Gradle dependency direction. Start with exact growth baselines only where current violations cannot be removed in the same change, then tighten each baseline as debt is retired.
  - **Verification:** Run the architecture test from the normal JVM suite; deliberately introduce one forbidden dependency, Android domain import, feature-to-feature import, public feature implementation, package mismatch, repository construction in a composable, and platform type in a ViewModel, and confirm each violation fails with its file and line.

- [x] **ARCH-0003 - Remove Cross-Feature And Reverse-Layer Dependencies** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/about/AboutScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloadHubScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/settings/AppSettingsRepository.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/backup/PreferencesBackupManager.kt`
  - **Problem:** About and Settings depend on each other, Browser imports Downloader, and the Downloader hub imports History. Lower layers also reverse direction: settings data imports downloader types, backup data imports `ui.theme`, downloader imports concrete data implementations, and shared updater UI imports data-layer state models.
  - **Impact:** Features cannot be understood, tested, reused, or extracted independently; changes ripple across unrelated packages and create cycles that package naming does not reveal.
  - **Fix:** Put navigation and cross-feature mapping in the app shell, expose small route/request contracts, and choose explicit ownership for composite flows. Treat About/legal as Settings-owned or app-owned, treat Downloader and History as one Downloads feature or coordinate them from the shell, and have Browser emit a neutral download request instead of starting Downloader code. Move shared settings/download option contracts below both data and features, and keep data independent of UI packages.
  - **Verification:** Add zero-baseline architecture rules that reject feature-to-feature imports and data-to-UI imports; generate the production dependency graph and confirm it is acyclic with all feature integration passing through app-shell contracts.

- [x] **ARCH-0004 - Centralize Dependency Composition And Worker Execution** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/AcquaApp.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/MainDependencies.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/MainActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloadActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt`
  - **Problem:** `MainActivity` and `DownloadActivity` each create separate `MainDependencies` graphs, browser/resolver activities construct repositories independently, and `YtDlpDownloadWorker` constructs settings, history, network, storage, and runtime implementations inside `doWork`. ViewModels depend mostly on concrete repositories and engines instead of narrow contracts.
  - **Impact:** Object lifetime and state ownership are inconsistent, expensive services can be duplicated, worker behavior is difficult to substitute in tests, and dependency direction is controlled by construction sites scattered throughout the app.
  - **Fix:** Own one application-scoped composition graph in `AcquaApp` or a DI container, expose narrow interfaces/use cases to ViewModels, and inject a single download executor into WorkManager through a custom `WorkerFactory`. Keep Android entry points as adapters that request dependencies from the application graph rather than constructing repositories directly.
  - **Verification:** Add identity/lifecycle tests for application-scoped dependencies and worker tests with fake executors; search production activities, workers, and composables for direct construction of repositories, managers, clients, engines, and storage implementations and require an empty result outside approved composition files.

- [ ] **ARCH-0005 - Move Infrastructure Orchestration Out Of Composables** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/updater/AppUpdatesViewModel.kt`
  - **Problem:** `BrowserScreen` constructs a second `SavedWebsiteRepository` and writes visit/icon state directly, while `SettingsScreen` owns update-check, APK download, installer, lifecycle-resume, and error state that overlaps `AppUpdatesViewModel`. UI composition therefore owns persistence and application workflows instead of rendering state and emitting intents.
  - **Impact:** State has multiple writers, update behavior can diverge between screens, configuration changes are harder to recover, and primary workflows require Android UI tests instead of deterministic ViewModel/use-case tests.
  - **Fix:** Route bookmark metadata through `BrowserViewModel`, consolidate update/download/install state in one updater controller or ViewModel, and represent UI actions as focused intents/events. Restrict composables to observing immutable state, invoking callbacks, and performing presentation-only effects such as launching an already-prepared platform request.
  - **Verification:** Add architecture checks preventing repository/manager/updater construction and blocking I/O in composable files; unit-test browser metadata and updater state transitions without Compose or a device, then verify recreation does not create duplicate work or lose authoritative state.

- [ ] **ARCH-0006 - Split Oversized Files And Narrow UI Contracts** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/ui/settings/AccentColorSelector.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/updater/AppUpdatesScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/DashboardScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/MediaPreviewCard.kt`
  - **Problem:** Five production files exceed 700 lines (`BrowserScreen` 1770, `SettingsScreen` 1352, `DownloaderScreen` 1155, `AccentColorSelector` 741, and `AppUpdatesScreen` 720), while `DownloaderViewModel` exceeds 500 lines. `DashboardScreen` exposes 18 parameters and `MediaMetadataContainer` exposes 16, indicating broad responsibilities and missing focused contracts.
  - **Impact:** Reviews require excessive context, unrelated changes collide in the same files, private components are difficult to test or reuse, and large parameter lists make call sites fragile.
  - **Fix:** Split files by cohesive responsibility, such as browser chrome/start page/WebView adapter/dialogs/extraction controller, settings sections/update route, downloader content/configuration/progress, and updater cards/dialogs. Replace broad public signatures with immutable route state and focused action groups where that clarifies ownership. Enforce 700-line production-file, 500-line ViewModel, and 15-parameter public-composable budgets with explicit shrinking baselines.
  - **Verification:** Run architecture budgets after every split, confirm each new file has one identifiable responsibility, and run focused screenshot/state tests to prove visual and behavioral equivalence without increasing any baseline.

- [ ] **ARCH-0007 - Normalize Package Ownership And Responsibility Naming** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/ui/theme/ThemePreferences.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/ui/updater/AppUpdateDialog.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/backup/BackupModels.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/updater/GitHubReleaseModels.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadQueueModels.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpModels.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/about/AboutModels.kt`
  - **Problem:** `YtDlpDownloadWorker` and `YtDlpDownloadCoordinator` also execute/enqueue direct HTTP downloads, `MediaDownloader` is an HTTP media client rather than the complete download coordinator, persistence lives under `ui.theme`, updater UI is outside its feature, and five generic `*Models.kt` buckets conceal the responsibility of their declarations. Browser ownership is additionally split across `auth`, `feature.browser`, `resolver.web`, and `data.session` without an explicit boundary map.
  - **Impact:** Names mislead maintainers about runtime behavior, package placement no longer predicts dependency direction, and generic files become convenient accumulation points for unrelated types.
  - **Fix:** Rename general worker/coordinator types to `DownloadWorker` and `DownloadWorkCoordinator`, give the direct HTTP client an implementation-specific name, move theme persistence into settings/data ownership, colocate updater presentation with the updater feature, and replace generic model buckets with responsibility-specific files where they contain more than one stable concept. Document the permitted Android entry-point and implementation subpackages for Browser and Downloads.
  - **Verification:** Add checks preventing new generic `Models`, `Utils`, `Helpers`, `Contracts`, or `StateSlices` production files; inspect every renamed type at call sites and verify its name covers all code paths, then confirm package/path ownership tests pass.

- [ ] **ARCH-0008 - Extract Gradle Modules Incrementally After Boundaries Are Clean** `[Medium]`
  - **Location:** `acqua-app/settings.gradle.kts` `acqua-app/app/build.gradle.kts` `acqua-app/app/src/main/java/dev/qtremors/acqua`
  - **Problem:** All 23,000+ lines of production Kotlin compile in one Android application module, so package boundaries provide no compile-time isolation and every feature sees the entire dependency classpath. Immediate large-scale modularization would be risky while the current package graph still contains cycles.
  - **Impact:** Incremental builds and focused testing scale poorly as the app grows, accidental dependencies remain cheap to add, and future extraction cost rises with every cross-package shortcut.
  - **Fix:** After architecture tests and package boundaries are clean, extract a small deliberate graph: `:core:domain` as pure JVM, `:core:data`, `:core:ui`, `:feature:downloads`, `:feature:browser`, `:feature:settings`, `:feature:updates`, and `:feature:onboarding`, with `:app` as the composition/navigation root. Do not split resolvers or tiny model groups into modules until ownership or build performance justifies it.
  - **Verification:** Move one boundary at a time with no behavior change, run affected module tests plus the architecture suite, inspect Gradle dependencies for feature-to-feature edges, and compare clean and incremental configuration/compile times before and after extraction.

- [ ] **STORAGE-0001 - Make Storage Permission Denial Recoverable** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/MainActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloadActivity.kt` `acqua-app/app/src/main/AndroidManifest.xml`
  - **Problem:** On Android 7 through 9, denying `WRITE_EXTERNAL_STORAGE` silently discards the pending download action. Permanent denial, permission revocation, and the path to system settings are not represented in UI state; notification denial likewise proceeds without explaining the reduced visibility of background work.
  - **Impact:** The primary download action can appear to do nothing, and users have no in-app recovery guidance after denial or revocation.
  - **Fix:** Model permission outcomes as typed UI state, show rationale before re-request where appropriate, distinguish temporary and permanent denial, provide a settings action, and explain notification-denial behavior before starting long-running work.
  - **Verification:** Test first denial, second/permanent denial, grant, revocation from Settings, activity recreation while the dialog is open, and notification denial on API 33+ across `MainActivity` and browser-launched `DownloadActivity`.

- [x] **STORAGE-0002 - Record The Actual MediaStore Destination Name** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/history/HistoryRepository.kt`
  - **Problem:** MediaStore saves record the requested `fileName` in history without querying the inserted row after provider-side collision handling. Unlike the legacy path's explicit `uniqueFile`, Android 10+ providers may choose a different display name, leaving history metadata out of sync with the saved file.
  - **Impact:** Users can see an incorrect filename in History and search for or share a record whose displayed metadata does not match the actual Downloads item.
  - **Fix:** Define a collision policy for MediaStore, resolve or query the final `DISPLAY_NAME` after insertion/finalization, and persist that authoritative name. Keep behavior consistent across MediaStore and legacy storage.
  - **Verification:** Pre-create same-named files and run concurrent identical downloads on API 29, current Android, and an OEM/provider variant; confirm no overwrite, every URI is distinct, and History shows the exact provider-visible filename.

### Frontend Tasks

- [ ] **COMPOSE-0001 - Restore User Workflow State** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/DashboardScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt`
  - **Problem:** The selected dashboard tab, About/legal overlay, downloader URL and resolved workflow, history query/filter/sort, and browser dialog/input state are held only in `remember` or ordinary `ViewModel` fields. They survive inconsistently across rotation and are lost after process recreation, returning users to the Downloader tab or discarding in-progress input.
  - **Impact:** Configuration changes and background process death interrupt normal browsing and download preparation, lose user input, and can leave the visible UI disconnected from background work that is still running.
  - **Fix:** Use `rememberSaveable` for small UI-only state and `SavedStateHandle` for screen state that belongs to a ViewModel. Persist only minimal identifiers and inputs, then reconstruct resolved media and WorkManager state from durable sources instead of putting large payloads in saved state.
  - **Verification:** Exercise rotation, locale/font-scale changes, split-screen resize, and Developer Options "Don't keep activities" on every top-level tab, dialogs, a populated downloader form, and an active background download; confirm the same destination and recoverable state return without replaying one-off events.

- [ ] **UI-0001 - Implement Adaptive Navigation And Content Layouts** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/DashboardScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/gradle/libs.versions.toml`
  - **Problem:** The app declares Material 3 adaptive dependencies but always renders a phone bottom bar and full-width single-column screens with fixed 20 dp margins. No window size or posture information is used, so tablets, foldables, landscape, and freeform windows receive stretched phone layouts rather than a rail, bounded content, or useful multi-pane presentation.
  - **Impact:** Important controls become widely separated, media previews and settings waste space, and navigation remains less efficient on large-screen and desktop-class devices.
  - **Fix:** Drive the shell from `currentWindowAdaptiveInfo()`, use `NavigationSuiteScaffold` or equivalent bar/rail switching, bound readable content widths, and introduce a list-detail or supporting pane where the browser/history/downloader flows benefit from it. Preserve state while the window class or fold posture changes.
  - **Verification:** Run Compose UI tests and manual QA at compact, medium, and expanded widths, portrait/landscape, split screen, a fold posture, and a resizable desktop window; verify navigation, panes, IME, and system bars remain reachable with no clipped or excessively stretched content.

- [ ] **FRONTEND-0001 - Cache GitHub API Stats And Handle Rate Limits** `[High]`
  - **Location:** `docs/scripts.js` `docs/index.html`
  - **Problem:** `fetchGitHubStats` and `fetchTotalReleaseDownloads` perform unauthenticated requests to `api.github.com` on every single page view (`/releases?per_page=100`, `/releases/latest`, and the repo endpoint). Unauthenticated GitHub API calls are strictly rate-limited to 60 requests per hour per IP. There is no client-side caching (`sessionStorage` or `localStorage`), no `AbortController` timeout, no maximum page loop boundary, and no handling for `403` or `429` rate limit responses. When rate-limited, promises reject, and all star, fork, and download stats display permanently unhandled `--` placeholders with console errors.
  - **Impact:** Live download counts and repository stats fail for visitors behind shared IPs or upon routine page refreshes, and an unbounded pagination loop risks hammering the GitHub API or hanging on slow networks.
  - **Fix:** Add a client-side storage cache with a reasonable TTL (e.g. 15-30 minutes) and ETag support; cap pagination to a bounded number of pages; add `AbortSignal.timeout(8000)`; detect HTTP 403/429 rate limit responses gracefully; and display fallback text or cached numbers instead of bare `--`.
  - **Verification:** Mock or trigger GitHub API HTTP 403 Rate Limit and slow 10-second response in browser DevTools; verify cached data is used on reload, no infinite loop occurs, and UI displays graceful fallback counts without throwing unhandled exceptions.

- [ ] **FRONTEND-0002 - Provide Progressive Enhancement For Reveal Animations** `[High]`
  - **Location:** `docs/index.html` `docs/styles.css` `docs/scripts.js`
  - **Problem:** CSS rule `.reveal` sets `opacity: 0; transform: translateY(18px) scale(0.985);` by default across all major content sections (hero copy, hero visual, bento grid cards, privacy panel, credits grid, FAQ list, download card). Visibility is only enabled via JavaScript adding `.visible` through `IntersectionObserver`. If JavaScript is disabled, blocked by content blockers, or fails to execute due to an uncaught script error in older browsers, the entire page content remains completely invisible and blank.
  - **Impact:** The website is inaccessible to users with JavaScript disabled, users on restricted enterprise browsers, or web crawlers/RSS readers that do not execute modern ECMAScript, resulting in total loss of content presentation.
  - **Fix:** Keep content visible by default in CSS and only apply hidden/reveal transforms when a `.js` class is added to the `<html>` root upon script execution, or add a `@media (scripting: none)` or `<noscript>` override in the `<head>` that forces `.reveal { opacity: 1 !important; transform: none !important; }`.
  - **Verification:** Disable JavaScript in browser settings and load `docs/index.html`; confirm all headings, bento cards, feature descriptions, credits, and FAQ items are fully legible and styled with no hidden or clipped elements.

### Backend Tasks

- [ ] **API-0001 - Resilient GitHub API Client Timeout And Pagination Guard** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/updater/GitHubApiClient.kt` `docs/scripts.js`
  - **Problem:** `GitHubApiClient` in the app executes blocking OkHttp network calls without custom connect/read/write timeouts, and `getReleases` fetches only a fixed 30 items without cursor or pagination support. In parallel, `docs/scripts.js` iterates releases without backoff or rate-limit tracking headers (`X-RateLimit-Remaining`, `X-RateLimit-Reset`).
  - **Impact:** Unstable network connections can cause updater hangs or silent update detection failures, while app and web clients handle GitHub API versioning and rate-limiting inconsistently.
  - **Fix:** Configure explicit connect, read, and write timeouts on OkHttp instances; inspect `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers to pause or back off requests; and align error contracts across web and mobile clients.
  - **Verification:** Simulate rate-limited and dropped socket connections via a local proxy or MockWebServer; verify client timeouts fire reliably and rate-limit reset times are respected.

### Security / Privacy Tasks

- [x] **SEC-0001 - Keep Browsing And Download History Out Of Automatic Backup** `[High]`
  - **Location:** `acqua-app/app/src/main/AndroidManifest.xml` `acqua-app/app/src/main/res/xml/backup_rules.xml` `acqua-app/app/src/main/res/xml/data_extraction_rules.xml` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/history/HistoryRepository.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/session/SavedWebsiteRepository.kt` `PRIVACY.md`
  - **Problem:** `android:allowBackup="true"` excludes encrypted sessions and WebView files but still permits cloud backup and device transfer of `acqua_history.db`, saved website origins, the last visited origin, browser UI preferences, and source/thumbnail URLs. The privacy policy says this information is processed on-device and does not disclose Android backup transfer.
  - **Impact:** Sensitive browsing and download metadata can leave the device through platform backup contrary to the documented privacy boundary and user expectations for a privacy-focused downloader.
  - **Fix:** Disable backup or replace the current denylist with an explicit allowlist limited to non-sensitive preferences. Decide deliberately whether device transfer differs from cloud backup, migrate safely, and update the privacy policy to match the implemented behavior.
  - **Verification:** Populate history, bookmarks, sessions, and settings, run Android backup/restore and device-transfer tests on pre-31 and 31+ rule paths, inspect the restored files, and confirm no origins, URLs, filenames, thumbnails, cookies, or browser state are transferred.

- [ ] **SEC-0002 - Stop Silent Network Reloads From Download History** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/history/HistoryRepository.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt` `PRIVACY.md`
  - **Problem:** Opening History automatically fetches every visible video or audio `thumbnailUrl` from its original third-party host. Those URLs are retained indefinitely in the history database, and the request happens without an explicit refresh/download action or disclosure.
  - **Impact:** Merely viewing local history reveals the user's IP address and access timing to past media hosts, may re-access sensitive or expiring URLs, consumes data, and conflicts with the documented action-initiated network model.
  - **Fix:** Persist a size-bounded local thumbnail at download time or show a local type placeholder. Define retention and cleanup for cached thumbnails, remove remote thumbnail URLs when no longer needed, and require an explicit user action for any network refresh.
  - **Verification:** Populate history with remote video/audio thumbnails, disable network and inspect traffic while opening and scrolling History, and confirm zero third-party requests; then delete and clear records and verify associated local thumbnail artifacts are removed.

- [x] **SEC-0003 - Separate Untrusted Diagnostics From User-Facing Errors** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/resolver/instagram/InstagramResolver.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt`
  - **Problem:** Instagram HTTP response snippets and arbitrary exception messages are composed into errors that the downloader displays and lets users copy. Internal English exception text is also used directly as UI copy. Remote bodies can contain account-specific data, unstable server details, markup, or long identifiers that should not cross the diagnostic boundary.
  - **Impact:** Sensitive response content can be exposed on screen or clipboard, error UX is inconsistent and unlocalizable, and internal implementation details become part of the public contract.
  - **Fix:** Introduce a typed failure taxonomy with localized user messages and explicit recovery actions. Keep bounded, redacted diagnostics behind a debug-only logging/reporting path that strips URLs, cookies, tokens, response bodies, filesystem paths, and account identifiers.
  - **Verification:** Use MockWebServer responses containing fake tokens, cookies, HTML, long URLs, and PII; assert release UI, clipboard text, WorkManager output, and logs contain only the localized safe message while debug diagnostics remain bounded and redacted.

- [ ] **SEC-0004 - Implement Content Security Policy And Strict Release URL Validation** `[High]`
  - **Location:** `docs/index.html` `docs/scripts.js`
  - **Problem:** `docs/index.html` contains no Content Security Policy (`<meta http-equiv="Content-Security-Policy">`). Furthermore, `applyLatestRelease` in `docs/scripts.js` updates anchor `href` attributes directly using `release.html_url` with only a superficial `startsWith("https://")` check, without verifying the origin matches `https://github.com/qtremors/acqua/releases/`. If GitHub API data is tampered with, intercepted, or redirected, external links could point to unintended destinations.
  - **Impact:** Lack of CSP leaves the site exposed to potential cross-site injection, unvetted CDN resource loading, or unapproved network requests; weak URL validation could allow malicious link redirection.
  - **Fix:** Add a strict meta Content Security Policy restricting `script-src 'self'`, `connect-src 'self' https://api.github.com`, `style-src 'self' https://fonts.googleapis.com`, `font-src https://fonts.gstatic.com`, and `img-src 'self' data: https:`; enforce strict origin and path prefix validation on `release.html_url` (must match `^https://github\.com/qtremors/acqua/releases/`).
  - **Verification:** Inspect browser DevTools Console and Security panel with CSP active; confirm unapproved external connections or inline script injections are blocked, and verify invalid or spoofed release URLs fall back to `latestReleaseFallback`.

### Performance Tasks

- [x] **PERF-0001 - Bound And Sample All Preview Bitmap Decoding** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/MediaPreviewCard.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt`
  - **Problem:** Preview responses are buffered up to 16 MiB and decoded at source resolution with `BitmapFactory.decodeByteArray` or `decodeStream`. The compressed-byte cap does not bound decoded pixel memory, and history rows can retain multiple full-resolution bitmaps while scrolling.
  - **Impact:** Large or maliciously dimensioned images can cause severe jank, memory churn, or an out-of-memory crash; ordinary high-resolution thumbnails waste memory and network bandwidth.
  - **Fix:** Use a lifecycle-aware image pipeline or a shared decoder that validates format/dimensions, caps pixels, performs bounds-first sampled decoding to the measured target size, coalesces requests, applies a bounded cache, and cancels work when content leaves composition. Reject decompression-bomb dimensions before allocation.
  - **Verification:** Test very large dimensions, high compression ratios, malformed images, 16 MiB payload boundaries, rapid pager changes, and long history scrolling under a constrained heap; use memory profiling to confirm bounded allocations and no OOM or stale decode work.

- [x] **PERF-0002 - Remove Native Runtime Update Work From Initial App Startup** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/MainActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpMaintenance.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpRuntime.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/MainDependencies.kt`
  - **Problem:** `MainActivity` creates ViewModels for every tab during first composition, and `SettingsViewModel.init` immediately runs yt-dlp maintenance. With the default auto-update setting this initializes the yt-dlp and FFmpeg runtime and may perform network/update work even when the user only wants another tab.
  - **Impact:** Cold start performs avoidable native extraction, filesystem cleanup, CPU, and network work, increasing time to first useful frame, memory usage, and launch-time variability.
  - **Fix:** Instantiate destination state holders lazily, keep version lookup/update out of constructors, and schedule update checks after first render under explicit network/battery constraints or on first engine use. Measure before adding a baseline profile for the true critical launch and download-entry journeys.
  - **Verification:** Add cold/warm startup macrobenchmarks with auto-update due and not due, trace class/native initialization and network traffic, and confirm first-frame/startup metrics no longer include yt-dlp or FFmpeg initialization when those features are unused.

- [ ] **PERF-0003 - Scale History Storage And Queries Beyond An In-Memory Full Scan** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/history/HistoryRepository.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryQuery.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryScreen.kt`
  - **Problem:** Each refresh opens a new `SQLiteOpenHelper`, loads the entire table, probes every downloaded URI, and then repeatedly filters, sorts, and summarizes full in-memory lists. The schema has no indexes for its hot timestamp, type, URL, or filename access patterns and no paging boundary.
  - **Impact:** History entry becomes increasingly slow and I/O-heavy as downloads accumulate, with long provider probes able to delay the whole screen and large lists consuming unnecessary memory.
  - **Fix:** Keep a process-scoped database, add explicit migrations and indexes based on measured queries, page ordered results, compute summaries/query results off the main thread, and bound or incrementally refresh existence checks instead of synchronously probing every record.
  - **Verification:** Seed realistic databases at 1,000, 10,000, and 100,000 entries with slow/missing content URIs; benchmark open, search, sort, scroll, delete, and summary updates and inspect query plans to confirm bounded memory and responsive first content.

- [x] **PERF-0004 - Throttle Persistent Progress And Notification Updates** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadNotificationController.kt`
  - **Problem:** Direct downloads invoke progress after every `DEFAULT_BUFFER_SIZE` read, and the worker responds with both `setProgressAsync` and `NotificationManager.notify` for every chunk. A large file can therefore generate hundreds of thousands of WorkManager database writes/futures and notification updates.
  - **Impact:** Progress bookkeeping can dominate download I/O, increase battery use, bloat scheduling work, and make large downloads slower or less reliable.
  - **Fix:** Separate byte copying from presentation updates and coalesce progress by elapsed time and meaningful percent/byte deltas, with a final forced emission. Keep the latest counters in memory and cap WorkManager/notification updates to a measured rate such as a few times per second.
  - **Verification:** Download multi-gigabyte responses from a local test server, count WorkManager progress writes and notification updates, and confirm updates stay under the configured rate while final byte counts, cancellation responsiveness, throughput, and UI smoothness remain correct.

- [ ] **PERF-0005 - Optimize Web Font Loading And Eliminate Layout Thrashing** `[Medium]`
  - **Location:** `docs/index.html` `docs/styles.css` `docs/scripts.js`
  - **Problem:** `docs/index.html` connects to Google Fonts CDN (`fonts.googleapis.com` and `fonts.gstatic.com`) downloading 8 weights of DM Sans and Manrope on every first visit, introducing third-party network waterfalls, potential layout shifts (CLS), and third-party IP leakage contrary to Acqua's privacy policy. In addition, SVG images (`assets/Acqua.svg`) lack explicit `width` and `height` dimensions in HTML, and `animateCounter` in `docs/scripts.js` mutates `element.innerText` on every `requestAnimationFrame` across multiple concurrent counter loops, causing continuous layout thrashing during animation.
  - **Impact:** Sub-optimal First Contentful Paint (FCP) and Largest Contentful Paint (LCP), visible font swap flash, layout shift, and main thread frame drops on low-power mobile devices.
  - **Fix:** Self-host font subsets as WOFF2 in `docs/assets/fonts/` with `font-display: swap`; declare explicit `width` and `height` attributes on all image and icon tags; and refactor `animateCounter` to use `element.textContent` instead of `innerText` under a single unified `requestAnimationFrame` ticker.
  - **Verification:** Run Lighthouse and WebPageTest performance audits; confirm zero third-party font requests, LCP under 1.2s, CLS equal to 0, and no layout thrashing warnings in the Chrome Performance profiler during counter animation.

### Reliability Tasks

- [x] **REL-0001 - Bound And Serialize Multi-Item Direct Downloads** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadQueueModels.kt` `README.md`
  - **Problem:** `downloadAll` enqueues every direct carousel item as a separately named unique work request, so all items are eligible to run concurrently. This bypasses the `DIRECT_WORK_QUEUE` chain used by processed downloads and contradicts the documented controlled, ordered queue.
  - **Impact:** Large carousels can saturate connections, storage, WorkManager, memory, and notification surfaces; aggregate progress and cancellation become harder to trust, especially on slow networks and low-end devices.
  - **Fix:** Put direct batch items into one durable ordered chain or a queue with an explicit small concurrency policy, preserve per-item retry/idempotency, and make aggregate progress and partial failure behavior reflect that policy. Align documentation after behavior is verified.
  - **Verification:** Enqueue the maximum candidate count against a controllable slow server and assert the concurrency ceiling, stable item order, bounded retries, accurate aggregate progress, cancellation of queued/running items, and clear partial-success reporting after process restart.

- [x] **REL-0002 - Adopt A Target-SDK-Compliant Long-Running Transfer Strategy** `[High]`
  - **Location:** `acqua-app/app/src/main/AndroidManifest.xml` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadNotificationController.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/build.gradle.kts`
  - **Problem:** The target-37 app runs every download and local media-processing phase as a WorkManager long-running worker using `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Android 15 limits `dataSync` foreground-service time, and Android 16 counts long-running WorkManager workers against job quota. The implementation has no quota/timeout recovery path and does not distinguish user-initiated transfer from local media processing.
  - **Impact:** Long or repeated downloads can be denied, stopped, or crash after platform limits are exhausted, leaving users with failed work and partial temporary output on supported target devices.
  - **Fix:** Investigate and implement the appropriate user-initiated data transfer job or direct foreground-service path for user-started downloads, and use the correct media-processing type/phase where applicable. Add durable phase/checkpoint state, timeout/quota-specific errors, startup cleanup/recovery, and a fallback policy for older API levels.
  - **Verification:** On Android 15, 16, and 17 devices/emulators, force shortened foreground-service timeouts and exhausted job quota, then test long download, processing, queued work, app backgrounding, process kill, retry, cancellation, and recovery without orphaned pending MediaStore rows or cache files.

- [x] **REL-0003 - Propagate Cancellation Into Direct Network Calls** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt`
  - **Problem:** Direct workers execute a synchronous OkHttp call and blocking copy loop inside `withContext(Dispatchers.IO)`, but the active `Call` is never cancelled when WorkManager cancels the coroutine and the loop has no cooperative cancellation check. The notification action can mark work cancelled while socket I/O and destination writes continue until the call returns or fails.
  - **Impact:** Cancellation is delayed or misleading, wastes data and battery, and can leave pending MediaStore entries or legacy partial files alive longer than users expect.
  - **Fix:** Expose a suspending download API that binds coroutine cancellation to `Call.cancel()`, checks cancellation during streaming, and guarantees destination rollback in `NonCancellable` cleanup where necessary. Preserve a distinct cancellation result instead of converting it to retry/failure.
  - **Verification:** Cancel during DNS/connect, a stalled response, prefix validation, and mid-stream copy on both MediaStore and legacy storage; assert prompt socket closure, no retry, no history row, no visible partial file, no pending MediaStore row, and no continued byte transfer.

### Infrastructure / DevOps Tasks

- [x] **BUILD-0001 - Prevent Unsigned Artifacts From Passing The Publish Path** `[High]`
  - **Location:** `acqua-app/app/build.gradle.kts` `DEVELOPMENT.md` `README.md`
  - **Problem:** When signing properties are absent, the normal `assembleRelease` task succeeds and produces unsigned release APKs with production names. There is no separate publish task or enforced certificate/application-ID verification, so a locally successful release build is not proof that artifacts are installable or signed by the official key.
  - **Impact:** An unsigned or incorrectly signed artifact can reach a release handoff, causing failed installs, broken upgrades, or loss of release identity.
  - **Fix:** Separate unsigned shrinker verification from publication, make the publish/official-release task fail without complete signing credentials, and automate `apksigner` certificate plus `aapt` application-ID checks against expected non-secret fingerprints and package metadata.
  - **Verification:** Confirm unsigned local shrinker verification still works, the publish task fails cleanly without credentials, succeeds with the official test fixture/configuration, and rejects a wrong key, wrong application ID, or unsigned ABI/universal output.

- [ ] **BUILD-0002 - Add A Reproducible Local Release Verification Gate** `[Medium]`
  - **Location:** `acqua-app/app/build.gradle.kts` `acqua-app/gradle/libs.versions.toml` `acqua-app/gradle/wrapper/gradle-wrapper.properties` `DEVELOPMENT.md`
  - **Problem:** Unit tests, lint, and the minified release build can be run manually, but no single checked-in local gate enforces them together, validates every ABI/universal artifact, records dependency verification, or fails on newly introduced lint warnings. The wrapper properties also omit a distribution checksum.
  - **Impact:** Release verification is easy to run incompletely and build inputs are less reproducible or tamper-evident than they should be for distributable APKs.
  - **Fix:** Add a lightweight local verification task/script that runs focused unit tests, lint with an intentional baseline/warning policy, minified release build, artifact enumeration, and signing/package checks. Pin the Gradle distribution checksum and enable Gradle dependency verification or equivalent checked-in checksums without adding GitHub CI unless separately requested.
  - **Verification:** Run the gate from a clean checkout and offline cache, then deliberately alter a dependency artifact, wrapper distribution, lint result, ABI output, signing state, and unit test to confirm each failure is detected with actionable output.

- [x] **BUILD-0003 - Make Build Convention Checks Match Their Names And Policy** `[Medium]`
  - **Location:** `acqua-app/build-logic/src/main/kotlin/dev/qtremors/acqua/buildlogic/AcquaAndroidApplicationConventionsPlugin.kt` `acqua-app/build-logic/src/test/kotlin/dev/qtremors/acqua/buildlogic/AcquaAndroidApplicationConventionsPluginTest.kt` `acqua-app/app/build.gradle.kts` `acqua-app/gradle/libs.versions.toml`
  - **Problem:** `verifyVersionCatalogFreshness` only checks that `[versions]`, `[libraries]`, and `[plugins]` headings exist, while `verifyReleaseVersionMetadata` only checks that version declarations are present. Neither task verifies dependency freshness, version-name/code agreement, numeric version policy, release identity, or invalid metadata, and the build-logic suite currently exercises only one production-string failure case.
  - **Impact:** Passing tasks communicate stronger guarantees than they provide, allowing stale dependencies or inconsistent release metadata to pass a gate presented as release-readiness verification.
  - **Fix:** Either rename each task to describe its limited structural check or implement the documented policy. Parse the version catalog and Gradle model where practical instead of matching raw text, validate that version code is derived consistently from version name, and add positive and negative TestKit coverage for every enforced condition.
  - **Verification:** Deliberately remove catalog sections, use malformed versions, mismatch version name/code, remove required metadata, and provide both fresh and intentionally stale dependency fixtures; confirm every supported policy has a deterministic TestKit assertion and an accurate task description.

- [ ] **LINT-0001 - Bring Android Lint To Green And Enforce It In Verification** `[High]`
  - **Location:** `acqua-app/app/build.gradle.kts` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/updater/AppUpdatesScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryScreen.kt` `acqua-app/app/src/main/res/values/strings.xml` `DEVELOPMENT.md`
  - **Problem:** `:app:lintDebug` currently fails with five `LocalContextGetResourceValueCall` errors, 115 warnings, and one hint, while the documented verification command and `verifyAcquaBuildConventions` still pass. The warnings include unused resources, Compose modifier ordering, configuration-based sizing, plural candidates, accessibility, obsolete SDK checks, and avoidable API usage.
  - **Impact:** A failing platform quality gate can be omitted from normal validation, configuration changes can show stale text, and new warnings can accumulate without a visible regression signal.
  - **Fix:** Fix all lint errors, classify and resolve actionable warnings, document any intentional suppressions next to the code, and establish an explicit warning/baseline policy that prevents new debt. Run release-oriented lint from the reproducible local verification gate and keep the report path visible on failure.
  - **Verification:** Run `:app:lintDebug` and `:app:lintRelease` from a clean checkout with zero errors; introduce a known Compose resource-read error and a new warning outside any approved baseline and confirm the local gate fails.

- [x] **LINT-0002 - Add Kotlin Formatting Naming And Complexity Analysis** `[Medium]`
  - **Location:** `acqua-app/build.gradle.kts` `acqua-app/app/build.gradle.kts` `acqua-app/build-logic` `acqua-app/app/src/main/java`
  - **Problem:** The project has no Kotlin formatter or semantic source analyzer enforcing import style, naming, complexity, function/file size, wildcard-import policy, forbidden dependencies, or consistent Compose signatures. Android lint does not cover many Kotlin maintainability concerns, and wildcard imports and oversized responsibilities already exist.
  - **Impact:** Formatting and naming drift create review noise, while complexity and responsibility growth remain subjective until a file becomes expensive to change.
  - **Fix:** Add one deterministic formatter and one focused Kotlin analyzer, such as ktlint/Spotless plus Detekt, configured through build logic. Enable rules for wildcard imports, naming, unused declarations, complexity, broad catches, long methods, too many functions, and Compose-aware conventions without duplicating architecture tests or applying mass unrelated formatting.
  - **Verification:** Run format checking and static analysis from the local gate, add representative invalid imports/names/complexity fixtures, and confirm failures are concise, reproducible, cacheable, and scoped to changed source where practical.

- [x] **INFRA-0001 - Add Static Site Verification And Link Checker Workflow** `[Medium]`
  - **Location:** `docs/` `docs/index.html` `README.md`
  - **Problem:** The `docs/` website is served via GitHub Pages but has no automated CI/CD workflow, static analysis, link-checking gate, or HTML/CSS validation. Dead links to releases, changelogs, or documentation can be committed without detection, and manual verification is required for every website update.
  - **Impact:** Broken links, invalid markup, or malformed assets can reach the production site unnoticed, degrading user and search engine experience.
  - **Fix:** Add a lightweight validation task or script that validates HTML semantic markup, checks internal and external links, lints CSS/JS, and verifies asset paths before deployment to GitHub Pages.
  - **Verification:** Run the validation script on `docs/` files, intentionally introducing a broken link and an invalid CSS rule; confirm the verification step fails with actionable diagnostics.

### Accessibility / Internationalization Tasks

- [ ] **A11Y-0001 - Give Toggle Controls Accessible Labels And Full-Row Actions** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderScreen.kt`
  - **Problem:** Settings, browser-session, metadata, and thumbnail toggles render a text column beside a standalone `Switch` without merging or associating the label semantics, and only the switch itself is actionable. Screen readers can announce an unlabeled "switch" and users with motor or switch-access needs must target the smallest control instead of the descriptive row.
  - **Impact:** Core privacy and download options are ambiguous or unnecessarily difficult to operate with TalkBack, Switch Access, and other accessibility services.
  - **Fix:** Make each complete row toggle the value, expose one merged semantic node with the setting title, description, role, and checked state, and clear duplicate child semantics while retaining a minimum 48 dp target and visible keyboard focus.
  - **Verification:** Add Compose semantics tests for every toggle and manually verify TalkBack, Switch Access, keyboard activation, focus indication, and double-tap behavior in both enabled states.

- [ ] **A11Y-0002 - Announce Download Progress And Results Semantically** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/MediaPreviewCard.kt`
  - **Problem:** The custom canvas progress indicator has no `progressBarRangeInfo`, its center text omits a percent label, and changing queued/running/retrying/completed/error states are not exposed through a live region. Per-item spinner/check state also changes visually without a state description.
  - **Impact:** TalkBack and other semantic consumers cannot determine progress or reliably learn when a long-running download succeeds, retries, fails, or is cancelled.
  - **Fix:** Add determinate/indeterminate progress semantics, localized state descriptions, and a restrained polite live region for phase and terminal-state changes. Mark decorative animation as such and expose item save state on the actionable control without producing duplicate announcements.
  - **Verification:** Add semantics assertions for queued, indeterminate, determinate, retry, success, failure, and cancellation states; manually confirm announcements are informative but not repeated for every byte-level progress update.

- [ ] **A11Y-0003 - Provide A Discoverable Edit Action For Saved Websites** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt`
  - **Problem:** A saved website can be edited only through an unlabeled long press on `WebsiteTile`. The UI provides no visible edit affordance or named custom accessibility action, and keyboard users have no discoverable equivalent.
  - **Impact:** TalkBack, Switch Access, keyboard, and mouse users can miss or be unable to use the website editing workflow.
  - **Fix:** Add an explicit overflow/edit action or a named custom accessibility action, expose a context-menu path for mouse/keyboard use, and keep open versus edit behavior unambiguous.
  - **Verification:** Verify edit is reachable by touch, TalkBack actions, Switch Access, Tab/Enter or context-menu keys, and mouse right-click without accidentally opening the website.

- [ ] **A11Y-0004 - Resolve Screen Reader Name Collisions And Mobile Navigation Focus** `[High]`
  - **Location:** `docs/index.html` `docs/scripts.js` `docs/styles.css`
  - **Problem:** The GitHub stats pill anchors (`<a class="stat-pill" aria-label="...">`) define static container `aria-label` attributes (`aria-label="Acqua repository stars and forks"` and `aria-label="Acqua total and latest release downloads"`) that conflict with and override the inner dynamic `aria-label` spans (`#gh-stars`, `#gh-forks`, etc.), hiding live count announcements from screen reader users. Additionally, opening the mobile navigation toggle does not trap focus, does not move focus into the opened navigation menu, and closing via Escape or backdrop click does not restore focus to the trigger button.
  - **Impact:** Screen reader users cannot hear the actual live star, fork, or download figures, and keyboard navigation becomes disorganized and trapped when interacting with the mobile menu.
  - **Fix:** Remove conflicting parent `aria-label`s on `.stat-pill` or replace them with `aria-describedby` pointing to structured child elements; add polite live announcements or semantic text descriptions for stats; implement proper focus management for the mobile navigation drawer (focus first link on open, trap Tab focus within menu when open, and return focus to `navToggle` on close).
  - **Verification:** Test using NVDA, VoiceOver, and TalkBack on desktop and mobile viewports; verify live counts are clearly announced, and verify keyboard Tab/Shift+Tab cycles cleanly through the open mobile menu and returns to the toggle upon Escape.

- [ ] **I18N-0001 - Support Non-English Locales In Carousel Media Navigation** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/resolver/web/RenderedPageResolverActivity.kt`
  - **Problem:** In `EXTRACTION_SCRIPT`, the selector for the carousel next button checks for English-only label text: `(label === 'next' || label.indexOf('next') >= 0)`. When a user visits Instagram or supported web targets in other languages (such as Spanish "Siguiente", French "Suivant", German "Weiter", Japanese "次へ", etc.), the button is never matched, `clickedNext` remains false, and only the first slide of a multi-item carousel is ever extracted.
  - **Impact:** Carousel downloads fail to detect or download subsequent slides for international users whose device or browser locale is not English.
  - **Fix:** Identify carousel navigation controls using language-agnostic selectors (e.g. `aria-label` matching SVG chevron paths, directional CSS classes, or sibling position relative to carousel indicators) rather than hardcoded English text strings; include localized fallback keyword sets for common languages.
  - **Verification:** Configure Android system language and WebView user-agent to Spanish, French, German, and Japanese; resolve multi-item carousel URLs in `RenderedPageResolverActivity` and confirm all items are successfully navigated and extracted.

- [ ] **I18N-0002 - Enforce Complete Localizable String And Resource Governance** `[Medium]`
  - **Location:** `acqua-app/build-logic/src/main/kotlin/dev/qtremors/acqua/buildlogic/AcquaAndroidApplicationConventionsPlugin.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/updater/AppUpdatesViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/onboarding/OnboardingViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/res/values/strings.xml`
  - **Problem:** `checkProductionStrings` targets a limited set of direct Compose/Toast patterns and passes while ViewModels, workers, updater state, and lower-layer exceptions still supply English fallback text to the UI. The resource file also contains dozens of lint-reported unused entries, duplicate values, inconsistent `hist_*` versus `history_*` prefixes, generic unprefixed names, and quantity strings that should use plurals.
  - **Impact:** User-visible text remains partially unlocalizable, lower layers define presentation copy, stale resources hide real usage, and inconsistent names make discovery and safe cleanup harder.
  - **Fix:** Complete the typed-failure boundary from `SEC-0003`, map stable failure/status codes to resources only at presentation edges, and strengthen string checks to cover UI-state/error constructors, WorkManager output, notifications, dialogs, snackbars, and fallback expressions. Remove verified unused resources, consolidate true duplicates, convert quantity-sensitive text to plurals, and adopt a consistent feature/semantic naming scheme without renaming stable resources gratuitously.
  - **Verification:** Inject hardcoded text through every supported UI-state, worker, notification, and exception path and confirm verification fails; run Android lint with no string/plural/unused-resource errors, switch locale during active screens, and verify displayed copy updates without stale `Context.getString` reads.

### Testing / Release Tasks

- [x] **TEST-0001 - Add WorkManager Download Integration And Recovery Tests** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt` `acqua-app/app/src/test` `acqua-app/app/src/androidTest`
  - **Problem:** Existing tests cover serialization, format selection, network detection, and repository basics, but no test executes the worker/coordinator lifecycle with constraints, foreground setup, retry, cancellation, process recreation, partial storage writes, or queue recovery.
  - **Impact:** The app's highest-risk persistent download path can regress while unit tests, lint, and compilation remain green.
  - **Fix:** Add WorkManager test-driver integration tests with injectable downloader/runtime/storage/clock dependencies and failure injection. Cover direct and processed success, transient/permanent failure, bounded retry, cancellation, ordered batches, process restart, MediaStore rollback, temp cleanup, history atomicity, and notification progress/actions.
  - **Verification:** Run the new suite deterministically on JVM where possible and on API 24, 29, 33, 35, and current target-device instrumentation; prove each terminal state leaves exactly the expected file, history, work, and temporary artifacts.

- [ ] **TEST-0002 - Add End-To-End UI, Accessibility, And State-Restoration Coverage** `[High]`
  - **Location:** `acqua-app/app/src/androidTest/java/dev/qtremors/acqua/feature` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/DashboardScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/auth/BrowserActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/resolver/web/RenderedPageResolverActivity.kt`
  - **Problem:** The only Compose screen instrumentation test targets About. There is no automated coverage for primary navigation, downloader states, permission denial, browser/resolver handoff, history actions, adaptive layouts, large fonts/RTL, semantic labels, or process-death restoration.
  - **Impact:** Core user journeys and premium Android quality requirements depend on manual checks and can regress without failing the build.
  - **Fix:** Build hermetic fakes for media resolution, WebView handoff, WorkManager state, permissions, storage, clock, and failure cases; add Compose/Espresso tests plus screenshot and accessibility assertions for all meaningful loading, empty, error, progress, completion, and recovery states.
  - **Verification:** Run the suite across light/dark, RTL, 1.0x/1.5x/2.0x fonts, compact/expanded windows, keyboard navigation, rotation, and activity/process recreation, with external network disabled.

- [ ] **TEST-0003 - Add Web Page Regression And API Mock Verification Suite** `[Medium]`
  - **Location:** `docs/` `acqua-app/app/src/test`
  - **Problem:** Existing automated tests only target the Android app components. The web documentation site (`docs/`), its dynamic GitHub API fetchers, responsive layouts, accessibility tree, and counter animations have zero automated test coverage.
  - **Impact:** Regressions in the landing page, broken API parsing, script errors, or layout breakage on mobile viewports can go undetected before deployment to GitHub Pages.
  - **Fix:** Add a headless browser or DOM test suite to verify stats fetching, error fallback, mobile navigation toggle, FAQ accordion state, and accessibility attributes.
  - **Verification:** Run the web test suite across emulated mobile and desktop viewports with both successful and mocked failed GitHub API responses.

- [ ] **TEST-0004 - Add Deterministic ViewModel And Orchestration Tests** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/settings/SettingsViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/updater/AppUpdatesViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/onboarding/OnboardingViewModel.kt` `acqua-app/app/src/test` `acqua-app/app/build.gradle.kts` `acqua-app/gradle/libs.versions.toml`
  - **Problem:** Existing JVM tests cover many pure models, formatters, clients, and resolution helpers, but there are no focused suites for Downloader, Settings, App Updates, or Onboarding ViewModel state machines. Concrete Android dependencies, hardcoded dispatchers, files, bundles, bitmaps, WorkManager types, and direct repositories make orchestration tests difficult despite developer documentation claiming coroutine-test and Turbine support that is not configured.
  - **Impact:** Cancellation, stale-request suppression, update/download transitions, one-off events, settings persistence, retry decisions, and recreation-sensitive behavior can regress while utility tests remain green.
  - **Fix:** Introduce narrow repository/use-case contracts, injectable dispatchers and clocks, typed worker/update gateways, and deterministic fakes. Add kotlinx-coroutines-test and Turbine or equivalent Flow assertions, then test success, failure, cancellation, concurrent replacement, retry, duplicate-event prevention, and restored-state scenarios for each ViewModel without device or network access.
  - **Verification:** Run each ViewModel suite repeatedly with virtual time and no Android runtime, confirm no real I/O or sleeps occur, and deliberately introduce stale emissions, swallowed cancellation, duplicate events, and dispatcher misuse to prove the tests fail deterministically.

### Documentation Tasks

- [ ] **DOC-0001 - Align Website Content With Core App Capabilities And Add Web Guide** `[Low]`
  - **Location:** `docs/index.html` `README.md` `DEVELOPMENT.md`
  - **Problem:** `docs/index.html` lists supported technologies and features that have drifted from the core app documentation. For example, `docs/index.html` lists Kotlin 2.4.10, but lacks documentation of supported download sites, video resolution limits, and APK architecture choices described in `README.md` and `DEVELOPMENT.md`. Conversely, `DEVELOPMENT.md` does not document the static documentation site architecture, assets, or maintenance instructions.
  - **Impact:** Developers and users encounter inconsistent feature claims and lack guidelines for maintaining the public website.
  - **Fix:** Synchronize website feature descriptions and supported formats with the latest app capabilities; add a dedicated "Website Architecture & Maintenance" section to `DEVELOPMENT.md` documenting `docs/` structure, GitHub API integration, and styling guidelines.
  - **Verification:** Review `docs/index.html`, `README.md`, and `DEVELOPMENT.md` side-by-side to verify all technical references, version tags, architecture diagrams, and links are consistent and up-to-date.

- [ ] **DOC-0002 - Correct App Architecture And Testing Documentation** `[Medium]`
  - **Location:** `DEVELOPMENT.md` `acqua-app/settings.gradle.kts` `acqua-app/app/src/main/AndroidManifest.xml` `acqua-app/app/src/main/java/dev/qtremors/acqua` `acqua-app/app/build.gradle.kts`
  - **Problem:** Developer documentation describes clean single-activity MVVM and names components such as `DownloadExecutionService`, `MediaFileWriter`, `DownloadHistoryStore`, `SessionManager`, and `YtDlpDownloader` that do not match the implementation. The manifest declares multiple activities, downloads use WorkManager, the documented package tree contains missing paths, and the testing section claims Robolectric, coroutine-test, and Turbine support that is absent from the dependency configuration.
  - **Impact:** Maintainers cannot rely on the architecture guide to locate ownership, understand runtime flow, choose the correct extension point, or run the tests the document promises.
  - **Fix:** Document the current architecture honestly before and during refactoring, including the actual activity/worker topology, composition root, resolver/download paths, persistence implementations, package boundaries, test dependencies, architecture rules, and staged target-module graph. Remove nonexistent names and clearly distinguish current state from intended architecture.
  - **Verification:** Cross-check every named class, package, module, command, dependency, manifest component, and diagram edge against the repository; run every documented verification command from a clean checkout and require all commands and paths to resolve exactly.
