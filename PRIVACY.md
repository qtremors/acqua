# Acqua Privacy Policy

Last updated: October 18, 2026

Acqua is a privacy-focused Android media downloader. It does not include ads,
analytics, telemetry, or an Acqua-operated account service.

## Information Acqua handles

Acqua handles only the information needed for features you choose to use:

- Links you paste, share, save, open from another app, or open in the in-app browser.
- Website cookies and storage created when you browse or sign in inside Acqua.
- Media metadata, previews, and files involved in resolving and saving downloads.
- App settings, saved website bookmarks, and local download history.
- Tracked GitHub repositories, release details, selected APKs, and notification preferences.
- A GitHub access token and username if you choose to connect an account.
- Installed app names, package identifiers, and versions used to match tracked releases.
- APK files downloaded for feed updates or Acqua updates, and feed backup files you select.

This information is processed on your device. Acqua does not send it to an
Acqua analytics service or developer-controlled server.

## Network requests

Downloading and resolving media necessarily contacts the website or media host
in the link you provide. The optional yt-dlp updater contacts its official update
channel. Opening project, privacy, release, issue, source, or license links from
the About page sends those links to your browser.

Feeds and Acqua update checks contact GitHub's API for repository and release
information. Feed searches can also fetch a repository README, and displaying
repository artwork can contact its image host. APK downloads contact the release
asset host and any download redirects. If you connect a GitHub account, API
requests use your token to access repositories allowed by that token, including
private repositories. Authenticated APK requests send the token only to trusted
GitHub API release-asset endpoints; other redirect destinations do not receive it.

The contacted services may receive normal network information such as your IP
address, request headers, and any session cookies you explicitly allow Acqua to
use. Their privacy policies and terms apply to those requests.

Optional transfer proxies apply to direct media requests and yt-dlp inspection
and downloads. They do not route in-app browser pages, site-specific extraction,
Feeds, or update checks. Proxy addresses are saved locally with download settings
and accepted requests; proxy credentials are not supported. A failed configured
proxy does not fall back to a direct transfer.

Optional segment removal contacts SponsorBlock with a partial video-ID hash and
only the removal categories you select. SponsorBlock receives your network
address as part of that request. This feature is off by default; ordinary
downloads do not request segment data.

## Browser sessions

The in-app browser stores cookies and website data in Android's app-private
storage. Browser-session extraction is optional. When enabled or explicitly
requested from the browser, Acqua attaches only matching session information to
the relevant media request.

You can explicitly save named cookie snapshots and their observed browser user
agents for websites you authorize. Available cookie attributes are retained;
older captures use conservative host/path limits and a local 24-hour expiry.
Partitioned cookies are excluded from standalone transfers. Saved snapshots and
accepted download authorizations are encrypted with an Android Keystore key and
are excluded from normal settings and History exports. Named transfers retain
their accepted session after refresh and do not switch to another account.
The browser itself continues to share one sign-in and website-storage context.

Removing a saved session deletes its retained authorizations and blocks queued
retries from using it. Website clearing also removes related saved sessions and
observed browser identities; clearing all browser data removes all saved
sessions and their encryption key. Session export is separate and requires an
explicit choice of document location after explaining that the unencrypted JSON
contains sign-in cookies and a user agent.

You can remove selected website data or clear all browser data from Settings.
Uninstalling Acqua also removes its app-private browser data under Android's
normal application-storage rules.

On supported WebViews, selected website clearing covers the whole parent website
and its subdomains, including cookies, cache, and site storage. On older WebViews,
Acqua explains before clearing that the fallback removes cookies for all
websites, while retaining unrelated bookmarks. Cookie removal waits for the
WebView completion callback and flush before Acqua refreshes the saved-site list.

Public direct-file inspection does not use browser-session cookies. GitHub
credentials are stored separately from browser sessions.

## Feeds, credentials, and installation

Acqua stores tracked repositories and their release and installed-app matching
details in app-private storage. Matching reads launcher-visible apps and any
explicitly mapped package on your device. The installed-app list is used locally
and is not uploaded to GitHub for matching.

When you connect an account, Acqua validates the token with GitHub. The saved
token is encrypted with an Android Keystore key; the username is kept in
app-private preferences. Signing out removes the saved token and username.
Tracked repositories and previously downloaded APKs are retained until removed
separately. Clearing browser data does not sign out the GitHub account.

Feed export writes a JSON file to the document location you choose. It contains
tracked repositories, cached release details, APK selections, preferences, and
saved package mappings and installed versions. It does not contain your GitHub
token or browser cookies. The export is not encrypted by Acqua, so a backup can
reveal private repository names and app information. Feed import reads the file
you select and merges its repositories and choices into the existing list;
downloads refresh asset endpoints from GitHub before using them. Settings backup
is separate and does not export feeds or GitHub credentials.

Update APKs and resumable partial downloads are kept in Acqua's private cache.
Android may clear this cache, and Acqua removes older cached updates during later
downloads. When you choose Install, Acqua shares read access to the selected APK
with Android's package installer. Android asks you to allow installations from
Acqua where required and handles installation confirmation.

## Downloads and history

Named command templates and their option text are stored locally. Accepted
downloads keep a resolved snapshot for retries. Template file and clipboard
imports show a preview before saving; exports contain validated option text and
names with sensitive patterns redacted, without cookies or GitHub credentials.
Exported documents are not encrypted by Acqua.

History file and clipboard exports contain source links, names, metadata, dates,
and file references for the records you choose. They do not copy downloaded
files or Android permission grants and are not encrypted. Imports preview new,
duplicate, and unavailable records before you apply them; existing records are
preserved. Relinking asks you to choose a file through Android's document picker
and retains its read grant. Pending previews use app-private cache for up to
24 hours and are removed on apply, explicit cancellation, or later expired-draft
cleanup. Android can also clear cached previews.

Downloaded files are saved to Android's shared Downloads storage or folders you
choose separately for photos, audio, and video. Custom choices retain read/write
folder grants and are saved locally with accepted downloads. Android's provider
controls access, availability, and synchronization of those folders.

Optional gallery hiding puts future files in an Acqua hidden subfolder with a
`.nomedia` marker. Some galleries ignore it; file managers can still show the
files, and Acqua does not encrypt them. Clearing Acqua's history removes records
but does not delete downloaded files. Files in shared storage remain until you
remove them yourself.

History also offers a separate, confirmed Delete files action for selected
downloads. Android or the storage provider may ask for permission. Acqua keeps
the History records and reports each file's deletion result. Undo for removed
History records cannot restore deleted files.

History previews are generated only from the downloaded file. Opening History
does not reload saved thumbnail URLs from third-party media hosts.

Save to History controls whether new links and completed files are added to
History. You can change the default or choose it for one download. Existing
History records are retained. Download progress, retry requests, duplicate-check
records, failure reports, browser sessions, and saved files can still remain when this option is
off; it does not clear other app data.

## Local failure reports

Acqua keeps up to 20 local failure reports for seven days, pruning older reports
when reports are accessed or the app starts. Reports include a task identifier,
time, processing phase, and redacted error summary. Optional extended diagnostics
add a bounded set of processing messages and code stack frames. Acqua removes
recognized credentials, identifying links, account details, and file paths before
saving these messages. Reports are stored in app-private storage and can be
cleared from Settings.

Reports are not uploaded automatically. Copy diagnostics puts the selected report
on your clipboard. Get help opens the project's issue page without attaching the
report; you choose what to share there.

Acqua disables Android cloud backup and device-transfer backup for all of its
app-private data. Browsing history, download history, saved websites, settings,
cookies, and sessions therefore are not transferred through Android backup.

## Permissions

Acqua requests network access to resolve and download media, notification and
foreground-service access to keep active downloads visible, vibration access for
haptic feedback, and legacy storage access only on Android versions that require
it for saving files.

For feed and app updates, Acqua declares permission to request package
installation. It also queries launcher apps to offer local installed-app
matching. Feed import and export use Android's document picker to read or write
the file you select.

## Data sharing

Acqua does not sell personal information. It does not share information with
advertising or analytics providers. Data reaches third-party websites only when
required by an action you initiate, such as browsing, resolving, downloading,
updating yt-dlp, or opening an external project link.

This also includes GitHub requests and release-asset downloads for the feed and
app-update actions described above. Documents exported to a cloud-backed
document provider are handled by the provider you select.

## Your choices

You can disable browser-session extraction, clear browser data, remove saved
bookmarks and history records, delete downloaded files, or uninstall the app.

For feeds, you can sign out of GitHub, untrack individual repositories, delete
exported backup files from their selected location, and clear Acqua's cache in
Android Settings to remove cached APKs. Clearing Acqua's app storage or
uninstalling it removes local credentials and tracked repositories. Files you
exported through the document picker, shared media downloads, and apps you
installed remain until you remove them separately.

## Changes and contact

Material policy changes are documented in the project repository. Questions and
issues can be submitted at <https://github.com/qtremors/acqua/issues>.
