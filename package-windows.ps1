$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$targetDir = Join-Path $projectRoot "target"
$releaseDir = Join-Path $targetDir "release\OxmanGameOptimizer"
$jarName = "OxmanGameOptimizer-1.0.0.jar"

& (Join-Path $projectRoot "mvnw.cmd") clean package
if ($LASTEXITCODE -ne 0) { throw "Maven no pudo construir la aplicacion." }

$presentMon = Join-Path $projectRoot "tools\PresentMon.exe"
if (-not (Test-Path -LiteralPath $presentMon -PathType Leaf)) {
    throw "Falta tools\PresentMon.exe; no se puede preparar la release."
}

New-Item -ItemType Directory -Path (Join-Path $releaseDir "tools") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $targetDir $jarName) -Destination $releaseDir -Force
Copy-Item -LiteralPath (Join-Path $targetDir "standalone-libs") -Destination $releaseDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "INICIAR-OXMAN.bat") -Destination $releaseDir -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "README.md") -Destination $releaseDir -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "THIRD-PARTY-NOTICES.md") -Destination $releaseDir -Force
Copy-Item -LiteralPath $presentMon -Destination (Join-Path $releaseDir "tools\PresentMon.exe") -Force

Write-Host "Release standalone creada en $releaseDir"
