@echo off
setlocal
set VERSION=9.3.1
set BASE=%USERPROFILE%\.gradle\vueo-bootstrap
set DIST=%BASE%\gradle-%VERSION%
set ZIP=%BASE%\gradle-%VERSION%-bin.zip
if exist "%DIST%\bin\gradle.bat" goto run
if not exist "%BASE%" mkdir "%BASE%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%BASE%' -Force"
if errorlevel 1 exit /b 1
:run
call "%DIST%\bin\gradle.bat" %*
