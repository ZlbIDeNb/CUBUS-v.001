param(
    [string]$Message = ""
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$expectedRemote = "https://github.com/ZlbIDeNb/CUBUS-v.001.git"

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & git -C $repositoryRoot @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is not installed or is not available in PATH."
}

$safeDirectories = @(git config --global --get-all safe.directory)
if ($safeDirectories -notcontains $repositoryRoot -and $safeDirectories -notcontains ($repositoryRoot -replace '\\', '/')) {
    git config --global --add safe.directory ($repositoryRoot -replace '\\', '/')
    if ($LASTEXITCODE -ne 0) {
        throw "Could not mark the CUBUS Runtime repository as safe."
    }
}

$insideWorkTree = git -C $repositoryRoot rev-parse --is-inside-work-tree
if ($LASTEXITCODE -ne 0 -or $insideWorkTree -ne "true") {
    throw "CUBUS Runtime Git repository was not found: $repositoryRoot"
}

$remoteNames = @(git -C $repositoryRoot remote)
if ($remoteNames -notcontains "origin") {
    Invoke-Git remote add origin $expectedRemote
}

$originUrl = git -C $repositoryRoot remote get-url origin
if ($LASTEXITCODE -ne 0 -or $originUrl.TrimEnd('/') -ne $expectedRemote.TrimEnd('/')) {
    throw "Unexpected GitHub remote: $originUrl"
}

$branch = git -C $repositoryRoot branch --show-current
if ($LASTEXITCODE -ne 0 -or $branch -ne "main") {
    throw "Backup is allowed only from the main branch. Current branch: $branch"
}

Write-Host "Checking GitHub for newer changes..." -ForegroundColor Cyan
Invoke-Git fetch origin main

& git -C $repositoryRoot merge-base --is-ancestor origin/main HEAD
if ($LASTEXITCODE -ne 0) {
    throw "GitHub contains changes that are not present locally. Backup stopped to prevent data loss."
}

$approvedPaths = @(
    ".gitattributes",
    ".gitignore",
    "README.md",
    "BACKUP_CUBUS_TO_GITHUB.cmd",
    "tools",
    "android/build.gradle.kts",
    "android/settings.gradle.kts",
    "android/gradle.properties",
    "android/gradlew",
    "android/gradlew.bat",
    "android/gradle",
    "android/app/build.gradle.kts",
    "android/app/proguard-rules.pro",
    "android/app/src",
    "backend/.env.example",
    "backend/requirements.txt",
    "backend/app",
    "backend/deploy",
    "backend/tests"
)

Invoke-Git add -- $approvedPaths

$stagedFiles = @(git -C $repositoryRoot diff --cached --name-only)
$unsafeFiles = @($stagedFiles | Where-Object {
    $_ -match '(^|/)(\.env|local\.properties)$' -or
    $_ -match '\.(apk|zip|sqlite3?|db|idsig|jks|keystore|pem|key)$' -or
    $_ -match '(^|/)(build|releases|server_auth|server_documents|server_registry)/'
})

if ($unsafeFiles.Count -gt 0) {
    Invoke-Git reset -- $unsafeFiles
    throw "Private or generated files were detected and removed from staging: $($unsafeFiles -join ', ')"
}

$secretValues = @{}
$environmentFile = Join-Path $repositoryRoot "backend\.env"
if (Test-Path -LiteralPath $environmentFile) {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*(APP_SECRET|CLIENT_BASE_TOKEN|DADATA_API_KEY|DADATA_SECRET_KEY)\s*=\s*(.+?)\s*$') {
            $value = $Matches[2].Trim('"', "'")
            if ($value.Length -ge 8) {
                $secretValues[$Matches[1]] = $value
            }
        }
    }
}

$secretMatches = New-Object System.Collections.Generic.List[string]
foreach ($relativePath in $stagedFiles) {
    $fullPath = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        continue
    }

    try {
        $content = [System.IO.File]::ReadAllText($fullPath)
    } catch {
        continue
    }

    foreach ($secretName in $secretValues.Keys) {
        if ($content.Contains($secretValues[$secretName])) {
            $secretMatches.Add("$relativePath [$secretName]")
        }
    }
}

if ($secretMatches.Count -gt 0) {
    Invoke-Git reset
    throw "A real secret was found in staged files. Nothing was committed: $($secretMatches -join ', ')"
}

& git -C $repositoryRoot diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    $unpushedCommitCount = [int](git -C $repositoryRoot rev-list --count origin/main..HEAD)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not check for unpushed local commits."
    }

    if ($unpushedCommitCount -eq 0) {
        Write-Host "CUBUS Runtime is already backed up. No changes found." -ForegroundColor Green
        exit 0
    }

    Write-Host "Sending $unpushedCommitCount existing local commit(s) to GitHub..." -ForegroundColor Cyan
    Invoke-Git push origin main
    Write-Host "CUBUS Runtime was backed up to GitHub successfully." -ForegroundColor Green
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = "backup: CUBUS Runtime $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
}

Write-Host "Creating a safe source-code backup..." -ForegroundColor Cyan
Invoke-Git commit -m $Message
Invoke-Git push origin main

Write-Host "CUBUS Runtime was backed up to GitHub successfully." -ForegroundColor Green
