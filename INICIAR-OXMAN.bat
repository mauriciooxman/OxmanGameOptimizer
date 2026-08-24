@echo off
setlocal
cd /d "%~dp0"

set "OXMAN_JAR=%~dp0OxmanGameOptimizer-1.1.0.jar"
set "OXMAN_LIBS=%~dp0standalone-libs"
set "OXMAN_PRESENTMON=%~dp0tools\PresentMon.exe"

rem PresentMon requires an elevated process to start its ETW trace session.
rem FLTMC succeeds only in an elevated administrator process and does not
rem depend on the Server service being enabled (unlike NET SESSION).
fltmc >nul 2>&1
if errorlevel 1 (
    if defined OXMAN_UAC_ATTEMPT (
        echo ERROR: No se pudo confirmar la elevacion de administrador.
        goto :error
    )
    set "OXMAN_UAC_ATTEMPT=1"
    set "OXMAN_LAUNCHER=%~f0"
    set "OXMAN_WORKDIR=%~dp0"
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try { Start-Process -FilePath $env:OXMAN_LAUNCHER -WorkingDirectory $env:OXMAN_WORKDIR -Verb RunAs -ErrorAction Stop ^| Out-Null; exit 0 } catch { exit 1 }"
    if errorlevel 1 (
        echo ERROR: No se concedieron permisos de administrador.
        goto :error
    )
    exit /b 0
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"

if not defined JAVA_EXE (
    echo ERROR: No se encontro Java 22.
    echo Configura JAVA_HOME con la ruta de un JDK 22.
    goto :error
)

set "OXMAN_JAVA=%JAVA_EXE%"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$line = ^& $env:OXMAN_JAVA -version 2^>^&1 ^| Select-Object -First 1; if ($line -notmatch 'version \"?([0-9]+)') { exit 1 }; if ([int]$Matches[1] -lt 22) { exit 1 }"
if errorlevel 1 (
    echo ERROR: Oxman requiere Java 22 o superior.
    goto :error
)

if not exist "%OXMAN_JAR%" (
    echo ERROR: No se encontro OxmanGameOptimizer-1.1.0.jar junto al launcher.
    goto :error
)

if not exist "%OXMAN_LIBS%\jna-5.15.0.jar" (
    echo ERROR: Falta standalone-libs\jna-5.15.0.jar.
    goto :error
)
if not exist "%OXMAN_LIBS%\jna-platform-5.15.0.jar" (
    echo ERROR: Falta standalone-libs\jna-platform-5.15.0.jar.
    goto :error
)
if not exist "%OXMAN_LIBS%\oshi-core-6.6.5.jar" (
    echo ERROR: Falta standalone-libs\oshi-core-6.6.5.jar.
    goto :error
)
if not exist "%OXMAN_PRESENTMON%" (
    echo ERROR: No se encontro tools\PresentMon.exe.
    goto :error
)

"%JAVA_EXE%" --module-path "%OXMAN_JAR%;%OXMAN_LIBS%" --module cl.oxman.oxmangameoptimizer/cl.oxman.oxmangameoptimizer.ApplicationLauncher
if errorlevel 1 goto :error

exit /b 0

:error
echo.
echo Oxman Game Optimizer finalizo con error.
echo Presiona una tecla para cerrar.
pause >nul
exit /b 1
