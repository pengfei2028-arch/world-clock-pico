@echo off
setlocal
rem Run manually in the user's normal Windows session; no elevation required.
rem Prefer the existing local SDK-configured project beside the Git checkout.
for %%I in ("%~dp0..\..\WorldClockSpatial") do set "PROJECT_DIR=%%~fI"
if not exist "%PROJECT_DIR%\local.properties" for %%I in ("%~dp0..\WorldClockSpatial") do set "PROJECT_DIR=%%~fI"
if not exist "%PROJECT_DIR%\gradlew.bat" (
  echo Project not found: %PROJECT_DIR%
  pause
  exit /b 1
)
if not defined JAVA_HOME set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java not found. Set JAVA_HOME to the installed Android Studio JBR.
  pause
  exit /b 1
)
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
for %%I in ("%PROJECT_DIR%\..\builds") do set "OUTPUT_DIR=%%~fI"
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
pushd "%PROJECT_DIR%"
echo Building WorldClock V57 offline with the installed SDK and cached dependencies.
echo Please keep this window open until it reports success or failure.
call gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest > "%OUTPUT_DIR%\WorldClock_v57_build.log" 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
type "%OUTPUT_DIR%\WorldClock_v57_build.log"
if not "%BUILD_RESULT%"=="0" goto failed
copy /y "app\build\outputs\apk\debug\app-debug.apk" "%OUTPUT_DIR%\WorldClock_v57_SharedTimeline.apk" >nul
if errorlevel 1 goto copy_failed
echo.
echo Build and unit tests completed. Simulator tests are still pending.
echo APK: %OUTPUT_DIR%\WorldClock_v57_SharedTimeline.apk
goto finished
:copy_failed
set "BUILD_RESULT=1"
echo APK copy failed. The compiled APK remains in app\build\outputs\apk\debug.
goto finished
:failed
echo.
echo Build failed. Please share the log path below; do not treat an older APK as this build.
:finished
echo LOG: %OUTPUT_DIR%\WorldClock_v57_build.log
popd
pause
exit /b %BUILD_RESULT%
