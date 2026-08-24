[CmdletBinding()]
param([string]$JdkHome = $env:JAVA_HOME)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$productName = "Oxman Game Optimizer"
$productVersion = "1.0.0"
$projectRoot = $PSScriptRoot
$targetDir = Join-Path $projectRoot "target"
$releaseRoot = Join-Path $targetDir "release"
$releaseDir = Join-Path $releaseRoot "OxmanGameOptimizer-v$productVersion"
$packageInput = Join-Path $targetDir "jpackage-input"
$jarName = "OxmanGameOptimizer-$productVersion.jar"
$icon = Join-Path $projectRoot "src\main\resources\cl\oxman\oxmangameoptimizer\company-logo-gold.ico"
$presentMon = Join-Path $projectRoot "tools\PresentMon.exe"

if ([string]::IsNullOrWhiteSpace($JdkHome)) {
    throw "Indique el JDK 22 con -JdkHome o JAVA_HOME."
}
$java = Join-Path $JdkHome "bin\java.exe"
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
    -not (Test-Path -LiteralPath $jpackage -PathType Leaf)) {
    throw "JdkHome no contiene java.exe y jpackage.exe: $JdkHome"
}
$javaVersion = (& $java --version | Select-Object -First 1) -join ""
if ($javaVersion -notmatch '^(?:openjdk|java) 22(?:\.|\s|$)') {
    throw "El empaquetado requiere JDK 22. Detectado: $javaVersion"
}
foreach ($requiredFile in @($icon, $presentMon)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Falta el archivo requerido: $requiredFile"
    }
}

Add-Type -AssemblyName System.Drawing
$loadedIcon = New-Object System.Drawing.Icon($icon)
$loadedIcon.Dispose()

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenCommand = if ($null -ne $maven) { $maven.Source } else { Join-Path $projectRoot "mvnw.cmd" }
if (-not (Test-Path -LiteralPath $mavenCommand -PathType Leaf)) {
    throw "No se encontro Maven ni mvnw.cmd."
}
$previousJavaHome = $env:JAVA_HOME
$previousHome = $env:HOME
$previousMavenOpts = $env:MAVEN_OPTS
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
    if ([string]::IsNullOrWhiteSpace($env:HOME)) { $env:HOME = $env:USERPROFILE }
    $env:MAVEN_OPTS = (("-Duser.home={0} {1}" -f $env:USERPROFILE, $previousMavenOpts).Trim())
    & $mavenCommand clean test
    if ($LASTEXITCODE -ne 0) { throw "mvn clean test fallo." }
    & $mavenCommand clean package
    if ($LASTEXITCODE -ne 0) { throw "mvn clean package fallo." }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:HOME = $previousHome
    $env:MAVEN_OPTS = $previousMavenOpts
}

New-Item -ItemType Directory -Path $packageInput -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $targetDir $jarName) -Destination $packageInput -Force
Copy-Item -Path (Join-Path $targetDir "standalone-libs\*.jar") -Destination $packageInput -Force
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
& $jpackage `
    --type app-image `
    --dest $releaseRoot `
    --name $productName `
    --app-version $productVersion `
    --vendor "Oxman" `
    --description "Oxman Game Optimizer" `
    --icon $icon `
    --input $packageInput `
    --main-jar $jarName `
    --main-class "cl.oxman.oxmangameoptimizer.ApplicationLauncher" `
    --java-options "-Dfile.encoding=UTF-8"
if ($LASTEXITCODE -ne 0) { throw "jpackage fallo." }

$jpackageOutput = Join-Path $releaseRoot $productName
if (-not (Test-Path -LiteralPath $jpackageOutput -PathType Container)) {
    throw "jpackage no genero la carpeta esperada: $jpackageOutput"
}
Move-Item -LiteralPath $jpackageOutput -Destination $releaseDir
$launcher = Join-Path $releaseDir "$productName.exe"

# jpackage app-image does not expose a requireAdministrator switch. Insert the
# standard Windows manifest into its native launcher without adding a wrapper.
$manifest = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
    <security><requestedPrivileges>
      <requestedExecutionLevel level="requireAdministrator" uiAccess="false"/>
    </requestedPrivileges></security>
  </trustInfo>
</assembly>
'@
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class OxmanResourceUpdater {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr BeginUpdateResource(string fileName, bool deleteExistingResources);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool UpdateResource(IntPtr update, IntPtr type, IntPtr name,
        ushort language, byte[] data, uint dataSize);
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool EndUpdateResource(IntPtr update, bool discard);
}
'@
$manifestBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($manifest)
$manifestWorkFile = Join-Path ([System.IO.Path]::GetTempPath()) ("oxman-launcher-{0}.exe" -f [guid]::NewGuid())
Copy-Item -LiteralPath $launcher -Destination $manifestWorkFile -Force
(Get-Item -LiteralPath $manifestWorkFile).IsReadOnly = $false
$update = [OxmanResourceUpdater]::BeginUpdateResource($manifestWorkFile, $false)
if ($update -eq [IntPtr]::Zero) {
    throw "No se pudo abrir el launcher para insertar el manifiesto UAC. Win32: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}
$updated = [OxmanResourceUpdater]::UpdateResource(
    $update, [IntPtr]24, [IntPtr]1, [uint16]0x0409, $manifestBytes, [uint32]$manifestBytes.Length)
if (-not $updated) {
    $errorCode = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
    [OxmanResourceUpdater]::EndUpdateResource($update, $true) | Out-Null
    throw "No se pudo insertar el manifiesto UAC. Win32: $errorCode"
}
if (-not [OxmanResourceUpdater]::EndUpdateResource($update, $false)) {
    throw "No se pudo guardar el manifiesto UAC. Win32: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}
(Get-Item -LiteralPath $launcher).IsReadOnly = $false
Copy-Item -LiteralPath $manifestWorkFile -Destination $launcher -Force
Remove-Item -LiteralPath $manifestWorkFile -Force

$toolsDir = Join-Path $releaseDir "tools"
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
Copy-Item -LiteralPath $presentMon -Destination (Join-Path $toolsDir "PresentMon.exe") -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "README.md") -Destination $releaseDir -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "THIRD-PARTY-NOTICES.md") -Destination $releaseDir -Force

foreach ($requiredOutput in @(
    $launcher,
    (Join-Path $releaseDir "runtime\bin\java.dll"),
    (Join-Path $releaseDir "app\$jarName"),
    (Join-Path $toolsDir "PresentMon.exe"),
    (Join-Path $releaseDir "README.md"),
    (Join-Path $releaseDir "THIRD-PARTY-NOTICES.md")
)) {
    if (-not (Test-Path -LiteralPath $requiredOutput -PathType Leaf)) {
        throw "La validacion final fallo; falta: $requiredOutput"
    }
}
$developerProfile = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
$hardcodedUserPath = Get-ChildItem -LiteralPath $releaseDir -Recurse -File |
    Where-Object Extension -In ".cfg", ".properties", ".xml", ".md", ".txt" |
    Select-String -SimpleMatch $developerProfile -ErrorAction SilentlyContinue
if ($null -ne $hardcodedUserPath) {
    throw "La release contiene una ruta absoluta del equipo de desarrollo."
}
Write-Host "Portable release READY: $releaseDir"
