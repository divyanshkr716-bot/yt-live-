@echo off
rem This project is intended to be built on Android/Linux environments. Use gradlew on AndroidIDE/Linux.
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed on this Windows environment. Please use AndroidIDE/Linux or install Gradle.
exit /b 1
