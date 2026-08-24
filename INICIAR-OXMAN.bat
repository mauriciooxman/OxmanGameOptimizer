@echo off
setlocal
cd /d "%~dp0"

rem PresentMon requires an elevated process to start its ETW trace session.
rem FLTMC succeeds only in an elevated administrator process and does not
rem depend on the Server service being enabled (unlike NET SESSION).
fltmc >nul 2>&1
if errorlevel 1 (
    set "OXMAN_LAUNCHER=%~f0"
    set "OXMAN_WORKDIR=%~dp0"
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try { Start-Process -FilePath $env:OXMAN_LAUNCHER -WorkingDirectory $env:OXMAN_WORKDIR -Verb RunAs -ErrorAction Stop ^| Out-Null; exit 0 } catch { exit 1 }"
    exit /b
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE if exist "%USERPROFILE%\.jdks\corretto-22.0.2\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\.jdks\corretto-22.0.2\bin\java.exe"

if not defined JAVA_EXE (
    echo ERROR: No se encontro Java 22.
    echo Configura JAVA_HOME con la ruta de un JDK 22.
    goto :error
)

set "OXMAN_JAR="
for %%F in ("target\OxmanGameOptimizer-*.jar") do if exist "%%~fF" set "OXMAN_JAR=%%~fF"

if not defined OXMAN_JAR (
    echo ERROR: No se encontro el build de Oxman en target.
    goto :error
)

if not exist "target\standalone-libs\*.jar" (
    echo ERROR: No se encontraron las dependencias standalone en target\standalone-libs.
    goto :error
)

if not exist "tools\PresentMon.exe" (
    echo ERROR: No se encontro tools\PresentMon.exe.
    goto :error
)

"%JAVA_EXE%" --module-path "%OXMAN_JAR%;target\standalone-libs" --module cl.oxman.oxmangameoptimizer/cl.oxman.oxmangameoptimizer.ApplicationLauncher
if errorlevel 1 goto :error

exit /b 0

:error
echo.
echo Oxman Game Optimizer finalizo con error.
echo Presiona una tecla para cerrar.
pause >nul
exit /b 1
