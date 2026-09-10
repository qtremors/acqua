# Acqua - Tasks

> **Project:** Acqua
>
> **Version:** 0.1.9
>
> **Last Updated:** 2026-09-10

---

### UI / UX Tasks

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

### Security / Privacy Tasks

- [ ] **SEC-0001 - Keep Browsing And Download History Out Of Automatic Backup** `[High]`
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

- [ ] **SEC-0003 - Separate Untrusted Diagnostics From User-Facing Errors** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/resolver/instagram/InstagramResolver.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt`
  - **Problem:** Instagram HTTP response snippets and arbitrary exception messages are composed into errors that the downloader displays and lets users copy. Internal English exception text is also used directly as UI copy. Remote bodies can contain account-specific data, unstable server details, markup, or long identifiers that should not cross the diagnostic boundary.
  - **Impact:** Sensitive response content can be exposed on screen or clipboard, error UX is inconsistent and unlocalizable, and internal implementation details become part of the public contract.
  - **Fix:** Introduce a typed failure taxonomy with localized user messages and explicit recovery actions. Keep bounded, redacted diagnostics behind a debug-only logging/reporting path that strips URLs, cookies, tokens, response bodies, filesystem paths, and account identifiers.
  - **Verification:** Use MockWebServer responses containing fake tokens, cookies, HTML, long URLs, and PII; assert release UI, clipboard text, WorkManager output, and logs contain only the localized safe message while debug diagnostics remain bounded and redacted.

### Performance Tasks

- [ ] **PERF-0001 - Bound And Sample All Preview Bitmap Decoding** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/MediaPreviewCard.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/history/HistoryScreen.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/browser/BrowserScreen.kt`
  - **Problem:** Preview responses are buffered up to 16 MiB and decoded at source resolution with `BitmapFactory.decodeByteArray` or `decodeStream`. The compressed-byte cap does not bound decoded pixel memory, and history rows can retain multiple full-resolution bitmaps while scrolling.
  - **Impact:** Large or maliciously dimensioned images can cause severe jank, memory churn, or an out-of-memory crash; ordinary high-resolution thumbnails waste memory and network bandwidth.
  - **Fix:** Use a lifecycle-aware image pipeline or a shared decoder that validates format/dimensions, caps pixels, performs bounds-first sampled decoding to the measured target size, coalesces requests, applies a bounded cache, and cancels work when content leaves composition. Reject decompression-bomb dimensions before allocation.
  - **Verification:** Test very large dimensions, high compression ratios, malformed images, 16 MiB payload boundaries, rapid pager changes, and long history scrolling under a constrained heap; use memory profiling to confirm bounded allocations and no OOM or stale decode work.

- [ ] **PERF-0002 - Remove Native Runtime Update Work From Initial App Startup** `[High]`
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

- [ ] **PERF-0004 - Throttle Persistent Progress And Notification Updates** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadNotificationController.kt`
  - **Problem:** Direct downloads invoke progress after every `DEFAULT_BUFFER_SIZE` read, and the worker responds with both `setProgressAsync` and `NotificationManager.notify` for every chunk. A large file can therefore generate hundreds of thousands of WorkManager database writes/futures and notification updates.
  - **Impact:** Progress bookkeeping can dominate download I/O, increase battery use, bloat scheduling work, and make large downloads slower or less reliable.
  - **Fix:** Separate byte copying from presentation updates and coalesce progress by elapsed time and meaningful percent/byte deltas, with a final forced emission. Keep the latest counters in memory and cap WorkManager/notification updates to a measured rate such as a few times per second.
  - **Verification:** Download multi-gigabyte responses from a local test server, count WorkManager progress writes and notification updates, and confirm updates stay under the configured rate while final byte counts, cancellation responsiveness, throughput, and UI smoothness remain correct.

### Reliability Tasks

- [ ] **REL-0001 - Bound And Serialize Multi-Item Direct Downloads** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloaderViewModel.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadQueueModels.kt` `README.md`
  - **Problem:** `downloadAll` enqueues every direct carousel item as a separately named unique work request, so all items are eligible to run concurrently. This bypasses the `DIRECT_WORK_QUEUE` chain used by processed downloads and contradicts the documented controlled, ordered queue.
  - **Impact:** Large carousels can saturate connections, storage, WorkManager, memory, and notification surfaces; aggregate progress and cancellation become harder to trust, especially on slow networks and low-end devices.
  - **Fix:** Put direct batch items into one durable ordered chain or a queue with an explicit small concurrency policy, preserve per-item retry/idempotency, and make aggregate progress and partial failure behavior reflect that policy. Align documentation after behavior is verified.
  - **Verification:** Enqueue the maximum candidate count against a controllable slow server and assert the concurrency ceiling, stable item order, bounded retries, accurate aggregate progress, cancellation of queued/running items, and clear partial-success reporting after process restart.

- [ ] **REL-0002 - Adopt A Target-SDK-Compliant Long-Running Transfer Strategy** `[High]`
  - **Location:** `acqua-app/app/src/main/AndroidManifest.xml` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/DownloadNotificationController.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadCoordinator.kt` `acqua-app/app/build.gradle.kts`
  - **Problem:** The target-37 app runs every download and local media-processing phase as a WorkManager long-running worker using `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Android 15 limits `dataSync` foreground-service time, and Android 16 counts long-running WorkManager workers against job quota. The implementation has no quota/timeout recovery path and does not distinguish user-initiated transfer from local media processing.
  - **Impact:** Long or repeated downloads can be denied, stopped, or crash after platform limits are exhausted, leaving users with failed work and partial temporary output on supported target devices.
  - **Fix:** Investigate and implement the appropriate user-initiated data transfer job or direct foreground-service path for user-started downloads, and use the correct media-processing type/phase where applicable. Add durable phase/checkpoint state, timeout/quota-specific errors, startup cleanup/recovery, and a fallback policy for older API levels.
  - **Verification:** On Android 15, 16, and 17 devices/emulators, force shortened foreground-service timeouts and exhausted job quota, then test long download, processing, queued work, app backgrounding, process kill, retry, cancellation, and recovery without orphaned pending MediaStore rows or cache files.

- [ ] **REL-0003 - Propagate Cancellation Into Direct Network Calls** `[High]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/downloader/YtDlpDownloadWorker.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/network/MediaDownloader.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt`
  - **Problem:** Direct workers execute a synchronous OkHttp call and blocking copy loop inside `withContext(Dispatchers.IO)`, but the active `Call` is never cancelled when WorkManager cancels the coroutine and the loop has no cooperative cancellation check. The notification action can mark work cancelled while socket I/O and destination writes continue until the call returns or fails.
  - **Impact:** Cancellation is delayed or misleading, wastes data and battery, and can leave pending MediaStore entries or legacy partial files alive longer than users expect.
  - **Fix:** Expose a suspending download API that binds coroutine cancellation to `Call.cancel()`, checks cancellation during streaming, and guarantees destination rollback in `NonCancellable` cleanup where necessary. Preserve a distinct cancellation result instead of converting it to retry/failure.
  - **Verification:** Cancel during DNS/connect, a stalled response, prefix validation, and mid-stream copy on both MediaStore and legacy storage; assert prompt socket closure, no retry, no history row, no visible partial file, no pending MediaStore row, and no continued byte transfer.

### Data / Storage / Platform Tasks

- [ ] **STORAGE-0001 - Make Storage Permission Denial Recoverable** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/MainActivity.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/feature/downloader/DownloadActivity.kt` `acqua-app/app/src/main/AndroidManifest.xml`
  - **Problem:** On Android 7 through 9, denying `WRITE_EXTERNAL_STORAGE` silently discards the pending download action. Permanent denial, permission revocation, and the path to system settings are not represented in UI state; notification denial likewise proceeds without explaining the reduced visibility of background work.
  - **Impact:** The primary download action can appear to do nothing, and users have no in-app recovery guidance after denial or revocation.
  - **Fix:** Model permission outcomes as typed UI state, show rationale before re-request where appropriate, distinguish temporary and permanent denial, provide a settings action, and explain notification-denial behavior before starting long-running work.
  - **Verification:** Test first denial, second/permanent denial, grant, revocation from Settings, activity recreation while the dialog is open, and notification denial on API 33+ across `MainActivity` and browser-launched `DownloadActivity`.

- [ ] **STORAGE-0002 - Record The Actual MediaStore Destination Name** `[Medium]`
  - **Location:** `acqua-app/app/src/main/java/dev/qtremors/acqua/platform/storage/MediaStorage.kt` `acqua-app/app/src/main/java/dev/qtremors/acqua/data/history/HistoryRepository.kt`
  - **Problem:** MediaStore saves record the requested `fileName` in history without querying the inserted row after provider-side collision handling. Unlike the legacy path's explicit `uniqueFile`, Android 10+ providers may choose a different display name, leaving history metadata out of sync with the saved file.
  - **Impact:** Users can see an incorrect filename in History and search for or share a record whose displayed metadata does not match the actual Downloads item.
  - **Fix:** Define a collision policy for MediaStore, resolve or query the final `DISPLAY_NAME` after insertion/finalization, and persist that authoritative name. Keep behavior consistent across MediaStore and legacy storage.
  - **Verification:** Pre-create same-named files and run concurrent identical downloads on API 29, current Android, and an OEM/provider variant; confirm no overwrite, every URI is distinct, and History shows the exact provider-visible filename.

### Testing / Release Tasks

- [ ] **TEST-0001 - Add WorkManager Download Integration And Recovery Tests** `[High]`
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

- [ ] **BUILD-0001 - Prevent Unsigned Artifacts From Passing The Publish Path** `[High]`
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
