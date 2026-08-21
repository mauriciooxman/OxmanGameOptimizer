$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$targetDir = Join-Path $projectRoot "target"
$inputDir = Join-Path $targetDir "jpackage-input"
$outputDir = Join-Path $targetDir "release"
$icon = Join-Path $projectRoot "src\main\resources\cl\oxman\oxmangameoptimizer\company-logo-gold.ico"
$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\jpackage.exe" } else { "jpackage.exe" }

& (Join-Path $projectRoot "mvnw.cmd") clean package dependency:copy-dependencies `
    "-DoutputDirectory=$inputDir" "-DincludeScope=runtime"
if ($LASTEXITCODE -ne 0) { throw "Maven no pudo construir la aplicacion." }
if (-not (Get-Command $jpackage -ErrorAction SilentlyContinue)) {
    throw "No se encontro jpackage. Configura JAVA_HOME con la ruta de un JDK 22 o superior."
}

Copy-Item (Join-Path $targetDir "OxmanGameOptimizer-1.0-SNAPSHOT.jar") $inputDir -Force
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

& $jpackage `
    --type app-image `
    --name OxmanGameOptimizer `
    --dest $outputDir `
    --input $inputDir `
    --main-jar OxmanGameOptimizer-1.0-SNAPSHOT.jar `
    --main-class cl.oxman.oxmangameoptimizer.ApplicationLauncher `
    --icon $icon `
    --vendor Oxman `
    --app-version 1.0
if ($LASTEXITCODE -ne 0) { throw "jpackage no pudo crear el ejecutable." }

Write-Host "Ejecutable creado en $outputDir\OxmanGameOptimizer\OxmanGameOptimizer.exe"
