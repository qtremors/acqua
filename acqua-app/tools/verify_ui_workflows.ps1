param()
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gradleArguments = @(
    ':core:data:testDebugUnitTest',
    '--tests', '*DownloadFoldersTest',
    '--tests', '*ProcessedPublicationFailureTest',
    '--tests', '*FileActionsSharingTest',
    '--tests', '*DownloadPermissionGateTest',
    ':feature:downloads:testDebugUnitTest',
    '--tests', '*WorkflowRestorationTest',
    '--tests', '*DownloadConfigSheetTest',
    '--tests', '*DownloaderControlLayoutTest',
    '--tests', '*BrowserResolutionCoordinatorTest',
    '--tests', '*DownloaderViewModelTest',
    ':feature:settings:testDebugUnitTest',
    '--tests', '*DownloadFoldersUiTest',
    '--tests', '*SettingsReadableRowsTest',
    '--tests', '*HighContrastUiTest',
    '--tests', '*LanguageSettingUiTest',
    ':app:testDebugUnitTest',
    '--tests', '*DashboardWorkflowTest',
    '--tests', '*SharedLinkDeliveryTest',
    '--tests', '*QuickDownloadActivityTest',
    '--tests', '*BrowserActivityTest',
    'analyzeKotlinSources',
    '--console=plain', '--offline', '--no-parallel', '--max-workers=1'
)
Push-Location -LiteralPath $projectDirectory
try {
    & .\gradlew.bat @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Focused UI workflow checks failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
