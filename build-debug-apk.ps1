param(
    [string]$SdkPath = "",
    [string]$JdkPath = "",
    [switch]$NoDownload
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$assetProject = Split-Path -Parent $MyInvocation.MyCommand.Path
$assetTools = Join-Path $assetProject ".asset-build-tools"
$assetGradleVersion = "8.9"

function Find-InstalledRoot {
    param([string[]]$Candidates, [string]$RequiredFile)
    foreach ($assetCandidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($assetCandidate)) { continue }
        $assetExpanded = [Environment]::ExpandEnvironmentVariables($assetCandidate)
        if (Test-Path -LiteralPath (Join-Path $assetExpanded $RequiredFile) -PathType Leaf) {
            return [System.IO.Path]::GetFullPath($assetExpanded)
        }
    }
    return $null
}

function Check-Gradle89 {
    param([string]$Executable)
    if ([string]::IsNullOrWhiteSpace($Executable) -or -not (Test-Path -LiteralPath $Executable)) {
        return $false
    }
    try {
        $assetOutput = (& $Executable --version 2>&1 | Out-String)
        return ($LASTEXITCODE -eq 0 -and $assetOutput -match 'Gradle 8\.9(\s|\r|\n)')
    } catch { return $false }
}

Write-Host "Checking Android build prerequisites..." -ForegroundColor Cyan

$assetSdkCandidates = @($SdkPath, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $assetSdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")
}
$assetSdk = Find-InstalledRoot -Candidates $assetSdkCandidates -RequiredFile "platforms\android-35\android.jar"
if ($null -eq $assetSdk) {
    throw "Android SDK Platform 35 not found. Install Android Studio and API 35 in SDK Manager, then run this script again."
}

$assetJavaCandidates = @($JdkPath, $env:JAVA_HOME)
if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
    $assetJavaCandidates += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
}
$assetJava = Find-InstalledRoot -Candidates $assetJavaCandidates -RequiredFile "bin\javac.exe"
if ($null -eq $assetJava) {
    throw "JDK 17 or later not found. Install Android Studio or pass -JdkPath with the JDK installation folder."
}

$assetJavaVersion = (& (Join-Path $assetJava "bin\java.exe") -version 2>&1 | Out-String)
$assetVersionMatch = [regex]::Match($assetJavaVersion, 'version\s+"(?<major>\d+)')
if ($assetVersionMatch.Success -and [int]$assetVersionMatch.Groups['major'].Value -lt 17) {
    throw "JDK is older than 17. Select the JDK included with a recent Android Studio version."
}

$env:JAVA_HOME = $assetJava
$env:ANDROID_HOME = $assetSdk
$env:ANDROID_SDK_ROOT = $assetSdk
Write-Host "Android SDK: $assetSdk"
Write-Host "JDK: $assetJava"

$assetGradle = Join-Path $assetTools "gradle-8.9\bin\gradle.bat"
if (-not (Check-Gradle89 -Executable $assetGradle)) {
    $assetGradle = $null

    # An already installed Gradle distribution is preferred over downloading.
    $assetGradleCommand = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($null -ne $assetGradleCommand -and (Check-Gradle89 -Executable $assetGradleCommand.Source)) {
        $assetGradle = $assetGradleCommand.Source
    }

    if ($null -eq $assetGradle -and -not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $assetWrapperCache = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-8.9-bin"
        if (Test-Path -LiteralPath $assetWrapperCache) {
            $assetCached = Get-ChildItem -Path $assetWrapperCache -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -like '*gradle-8.9\bin\gradle.bat' } |
                Select-Object -First 1
            if ($null -ne $assetCached -and (Check-Gradle89 -Executable $assetCached.FullName)) {
                $assetGradle = $assetCached.FullName
            }
        }
    }

    if ($null -eq $assetGradle) {
        if ($NoDownload) {
            throw "Gradle 8.9 is missing and -NoDownload was set. Install Gradle 8.9 or rerun without -NoDownload."
        }
        New-Item -ItemType Directory -Path $assetTools -Force | Out-Null
        $assetDownload = Join-Path $assetTools "gradle-8.9-bin.zip"
        $assetUrl = "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
        Write-Host "Downloading the official Gradle 8.9 distribution..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $assetUrl -OutFile $assetDownload -UseBasicParsing

        # Verify the official SHA-256 before extracting downloaded executables.
        $assetChecksumFile = Join-Path $assetTools "gradle-8.9-bin.zip.sha256"
        Invoke-WebRequest -Uri "$assetUrl.sha256" -OutFile $assetChecksumFile -UseBasicParsing
        $assetChecksumText = Get-Content -LiteralPath $assetChecksumFile -Raw
        $assetChecksum = (($assetChecksumText -split '\s+')[0]).Trim().ToLowerInvariant()
        if ($assetChecksum -notmatch '^[0-9a-f]{64}$') {
            throw "Official Gradle checksum was unavailable or invalid. Aborting without executing downloaded files."
        }
        $assetHash = (Get-FileHash -LiteralPath $assetDownload -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($assetHash -ne $assetChecksum) {
            throw "Gradle download SHA-256 mismatch. Aborting the build."
        }
        Expand-Archive -LiteralPath $assetDownload -DestinationPath $assetTools -Force
        $assetGradle = Join-Path $assetTools "gradle-8.9\bin\gradle.bat"
        if (-not (Check-Gradle89 -Executable $assetGradle)) {
            throw "Gradle 8.9 was not found after extraction."
        }
    }
}

Write-Host "Building APK using Gradle 8.9..." -ForegroundColor Cyan
Push-Location $assetProject
try {
    & $assetGradle --project-dir $assetProject --no-daemon --console=plain ":app:assembleDebug"
    $assetBuildExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($assetBuildExit -ne 0) {
    throw "Gradle build failed (exit code $assetBuildExit). See build errors above."
}

$assetApk = Join-Path $assetProject "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $assetApk -PathType Leaf)) {
    throw "Build reported success but the APK file is missing."
}

# Validate the APK's minimum ZIP structure independently of the Gradle exit code.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetArchive = [System.IO.Compression.ZipFile]::OpenRead($assetApk)
try {
    $assetEntries = @($assetArchive.Entries | ForEach-Object { $_.FullName })
    if (-not ($assetEntries -contains "AndroidManifest.xml") -or -not ($assetEntries -contains "classes.dex")) {
        throw "The generated APK is missing its Android manifest or main DEX."
    }
} finally {
    $assetArchive.Dispose()
}

$assetSignTool = Get-ChildItem -Path (Join-Path $assetSdk "build-tools") -Filter "apksigner.bat" -File -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($null -ne $assetSignTool) {
    & $assetSignTool.FullName verify --verbose $assetApk
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
}

$assetDist = Join-Path $assetProject "dist"
New-Item -ItemType Directory -Path $assetDist -Force | Out-Null
$assetTimestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$assetOutput = Join-Path $assetDist "AssetAudit-debug-$assetTimestamp.apk"
Copy-Item -LiteralPath $assetApk -Destination $assetOutput
$assetOutputHash = (Get-FileHash -LiteralPath $assetOutput -Algorithm SHA256).Hash
Write-Host "APK_BUILD_OK: $assetOutput" -ForegroundColor Green
Write-Host "SHA256: $assetOutputHash"
