@echo off
setlocal

set "BASEDIR=%~dp0"
set "WRAPPER_PROPERTIES=%BASEDIR%.mvn\wrapper\maven-wrapper.properties"

if not exist "%WRAPPER_PROPERTIES%" (
  echo Missing Maven wrapper properties: %WRAPPER_PROPERTIES% 1>&2
  exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%WRAPPER_PROPERTIES%"') do set "DISTRIBUTION_URL=%%B"
if "%DISTRIBUTION_URL%"=="" (
  echo Missing distributionUrl in %WRAPPER_PROPERTIES% 1>&2
  exit /b 1
)

if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
for %%F in ("%DISTRIBUTION_URL%") do set "DIST_FILE=%%~nxF"
set "DIST_NAME=%DIST_FILE:-bin.zip=%"
set "INSTALL_ROOT=%MAVEN_USER_HOME%\wrapper\dists"
set "INSTALL_DIR=%INSTALL_ROOT%\%DIST_NAME%"
set "MVN_CMD=%INSTALL_DIR%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto run_maven

where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 is required. Install JDK 21 or set JAVA_HOME before running mvnw.cmd. 1>&2
  exit /b 1
)

where powershell >nul 2>nul
if errorlevel 1 (
  echo PowerShell is required to bootstrap Maven from %DISTRIBUTION_URL%. 1>&2
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $url='%DISTRIBUTION_URL%'; $installRoot='%INSTALL_ROOT%'; $installDir='%INSTALL_DIR%'; $tmp=Join-Path $env:TEMP ('mvnw-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Force -Path $tmp | Out-Null; New-Item -ItemType Directory -Force -Path $installRoot | Out-Null; $zip=Join-Path $tmp '%DIST_FILE%'; Write-Host ('Downloading Maven from ' + $url); Invoke-WebRequest -Uri $url -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $tmp -Force; $src=Get-ChildItem -Path $tmp -Directory -Filter 'apache-maven-*' | Select-Object -First 1; if (-not $src) { throw 'Downloaded archive did not contain an apache-maven-* directory.' }; if (Test-Path $installDir) { throw ('Maven cache exists but mvn.cmd is missing: ' + $installDir) }; Move-Item -Path $src.FullName -Destination $installDir; Remove-Item -Recurse -Force $tmp"
if errorlevel 1 exit /b 1

:run_maven
"%MVN_CMD%" %*
