@echo off
setlocal
rem Manual emulator verification. Never selects or installs to a physical headset.
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "SERIAL=emulator-5554"
for %%I in ("%~dp0..\..\WorldClockSpatial") do set "PROJECT_DIR=%%~fI"
for %%I in ("%PROJECT_DIR%\..\builds") do set "OUTPUT_DIR=%%~fI"
set "RESULT_DIR=%OUTPUT_DIR%\v57-verification"
if not exist "%RESULT_DIR%" mkdir "%RESULT_DIR%"
if not exist "%ADB%" goto no_adb
"%ADB%" devices -l
"%ADB%" -s %SERIAL% get-state >nul 2>&1
if errorlevel 1 goto no_emulator
if not exist "%OUTPUT_DIR%\WorldClock_v57_SharedTimeline.apk" goto no_apk
if not defined JAVA_HOME set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java
if not exist "%PROJECT_DIR%\gradlew.bat" goto no_project
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
echo Rebuilding the corrected verification package offline.
echo Build log: %RESULT_DIR%\test-build.log
pushd "%PROJECT_DIR%"
call gradlew.bat --offline :app:assembleDebugAndroidTest > "%RESULT_DIR%\test-build.log" 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
popd
type "%RESULT_DIR%\test-build.log"
if not "%BUILD_RESULT%"=="0" goto build_failed
if not exist "%PROJECT_DIR%\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk" goto no_apk
echo Installing V57 and its verification package on %SERIAL%.
"%ADB%" -s %SERIAL% install -r "%OUTPUT_DIR%\WorldClock_v57_SharedTimeline.apk"
if errorlevel 1 goto failed
"%ADB%" -s %SERIAL% install -r "%PROJECT_DIR%\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if errorlevel 1 goto failed
echo Running the shared timeline and fade checks. Keep the simulator open.
"%ADB%" -s %SERIAL% shell am instrument -w -r -e class com.wupengfei.worldclock.SharedTimelineIntegrationTest com.wupengfei.worldclock.test/androidx.test.runner.AndroidJUnitRunner > "%RESULT_DIR%\integration.log" 2>&1
set "TEST_RESULT=%ERRORLEVEL%"
type "%RESULT_DIR%\integration.log"
"%ADB%" -s %SERIAL% logcat -d -v threadtime -s TemperatureAcceptance:I WorldClockTemperature:I AndroidRuntime:E > "%RESULT_DIR%\runtime.log" 2>&1
rem These Android-display captures are diagnostic only; PICO XR can be absent.
for %%F in (v57-world v57-temperature v57-restored) do "%ADB%" -s %SERIAL% pull "/sdcard/Android/data/com.wupengfei.worldclock/files/%%F-android.png" "%RESULT_DIR%\%%F-android.png"
rem Use the installed official CLI if it is on this user's normal PATH.
rem No automatic downloads, SDK repairs or device fallback.
where /q pico-cli
if errorlevel 1 goto capture_unavailable
echo Capturing the simulator with the official PICO CLI.
call pico-cli capture screenshot --device %SERIAL% --out "%RESULT_DIR%\v57-after-test-pico.png" > "%RESULT_DIR%\capture.log" 2>&1
if errorlevel 1 echo PICO capture failed. See capture.log; visual review remains pending.
goto check_result
:capture_unavailable
echo PICO CLI is not on PATH. XR visual review remains pending.> "%RESULT_DIR%\capture.log"
:check_result
if not "%TEST_RESULT%"=="0" goto failed
findstr /C:"OK (1 test)" "%RESULT_DIR%\integration.log" >nul
if errorlevel 1 goto failed
echo.
echo Automated simulator checks passed. Screenshots still need visual review.
echo Results: %RESULT_DIR%
pause
exit /b 0
:no_emulator
echo.
echo Open the usual PICO simulator and wait until it starts, then run this file again.
echo Expected device: %SERIAL%. No headset or other simulator was modified.
pause
exit /b 1
:no_adb
echo Android platform-tools not found: %ADB%
pause
exit /b 1
:no_apk
echo V57 app or test APK is missing. Run build-local-v57.cmd first.
pause
exit /b 1
:no_java
echo Java not found. Set JAVA_HOME to the installed Android Studio JBR.
pause
exit /b 1
:no_project
echo Project not found: %PROJECT_DIR%
pause
exit /b 1
:build_failed
echo Test package build failed. No APK was installed in this run.
echo Build log: %RESULT_DIR%\test-build.log
pause
exit /b 1
:failed
echo.
echo Verification did not complete successfully. Share that it finished so the logs can be reviewed.
echo Results: %RESULT_DIR%
pause
exit /b 1
