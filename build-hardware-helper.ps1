[CmdletBinding()]
param(
    [string]$DotnetPath,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "target\hardware-helper"),
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = $PSScriptRoot
$helperProject = Join-Path $projectRoot "hardware\OxmanHardwareSensor\OxmanHardwareSensor.csproj"
$testProject = Join-Path $projectRoot "hardware\OxmanHardwareSensor.Tests\OxmanHardwareSensor.Tests.csproj"
$licenseRoot = Join-Path $projectRoot "licenses"
$redistributedLicenses = [ordered]@{
    "LibreHardwareMonitor-0.9.6" = @("LICENSE", "README.md", "THIRD-PARTY-NOTICES.txt")
    "BlackSharp.Core-1.0.7" = @("LICENSE", "README.md")
    "DiskInfoToolkit-1.1.2" = @("LICENSE", "README.md")
    "RAMSPDToolkit-NDD-1.4.2" = @("LICENSE", "README.md")
    "HidSharp-2.6.4" = @("LICENSE.txt", "README.md")
    "Mono.Posix.NETStandard-1.0.0" = @("LICENSE", "PATENTS.TXT", "README.md")
}

function Resolve-DotnetExecutable([string]$RequestedPath) {
    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        $candidate = $RequestedPath
        if (Test-Path -LiteralPath $candidate -PathType Container) {
            $candidate = Join-Path $candidate "dotnet.exe"
        }
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            throw "No se encontro dotnet.exe en: $RequestedPath"
        }
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    $command = Get-Command dotnet.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }

    $userProfile = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $localSdk = Join-Path $userProfile ".dotnet\dotnet.exe"
    if (Test-Path -LiteralPath $localSdk -PathType Leaf) {
        return (Resolve-Path -LiteralPath $localSdk).Path
    }
    throw "Se requiere el SDK .NET 8 para compilar OxmanHardwareSensor."
}

$dotnet = Resolve-DotnetExecutable $DotnetPath
$dotnetVersion = (& $dotnet --version | Select-Object -First 1) -join ""
if ($dotnetVersion -notmatch '^8\.') {
    throw "El helper requiere SDK .NET 8. Detectado: $dotnetVersion"
}

foreach ($requiredFile in @($helperProject, $testProject)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Falta el archivo requerido para el helper: $requiredFile"
    }
}
foreach ($licenseName in $redistributedLicenses.Keys) {
    foreach ($licenseFile in $redistributedLicenses[$licenseName]) {
        $requiredFile = Join-Path $licenseRoot (Join-Path $licenseName $licenseFile)
        if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
            throw "Falta el archivo de licencia requerido para el helper: $requiredFile"
        }
    }
}

if (-not $SkipTests) {
    & $dotnet test $testProject -c Release --nologo
    if ($LASTEXITCODE -ne 0) { throw "Las pruebas de OxmanHardwareSensor fallaron." }
}

$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "target"))
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$targetPrefix = $targetRoot.TrimEnd('\') + '\'
if (-not $outputPath.StartsWith($targetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "La salida del helper debe estar dentro de target: $outputPath"
}
if (Test-Path -LiteralPath $outputPath) {
    Remove-Item -LiteralPath $outputPath -Recurse -Force
}
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null

& $dotnet publish $helperProject -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:PublishTrimmed=false -o $outputPath --nologo
if ($LASTEXITCODE -ne 0) { throw "La publicacion self-contained del helper fallo." }

$helperLicenseDir = Join-Path $outputPath "licenses"
New-Item -ItemType Directory -Path $helperLicenseDir -Force | Out-Null
foreach ($licenseName in $redistributedLicenses.Keys) {
    Copy-Item -LiteralPath (Join-Path $licenseRoot $licenseName) `
        -Destination (Join-Path $helperLicenseDir $licenseName) -Recurse -Force
}

$dotnetRoot = Split-Path -Parent $dotnet
$dotnetLicenseDir = Join-Path $helperLicenseDir "dotnet-runtime-8.0"
New-Item -ItemType Directory -Path $dotnetLicenseDir -Force | Out-Null
foreach ($runtimeNotice in @("LICENSE.txt", "ThirdPartyNotices.txt")) {
    $source = Join-Path $dotnetRoot $runtimeNotice
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Falta el aviso legal del runtime .NET: $source"
    }
    Copy-Item -LiteralPath $source -Destination $dotnetLicenseDir -Force
}

$helperExecutable = Join-Path $outputPath "OxmanHardwareSensor.exe"
if (-not (Test-Path -LiteralPath $helperExecutable -PathType Leaf)) {
    throw "No se genero el helper esperado: $helperExecutable"
}
Write-Host "Hardware sensor helper SELF-CONTAINED: $helperExecutable"
