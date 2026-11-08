@echo off
setlocal
if not exist "%~dp0out\tetris.jar" (
    powershell.exe -NoProfile -File "%~dp0build.ps1" -Task Build
    if errorlevel 1 exit /b 1
)
set "TETRIS_JAVAW="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "TETRIS_JAVAW=%JAVA_HOME%\bin\javaw.exe"
for /f "delims=" %%J in ('where javaw.exe 2^>nul') do if not defined TETRIS_JAVAW set "TETRIS_JAVAW=%%J"
if not defined TETRIS_JAVAW if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javaw.exe" set "TETRIS_JAVAW=%ProgramFiles%\Android\Android Studio\jbr\bin\javaw.exe"
if not defined TETRIS_JAVAW (
    echo Java 8 or later is required. Set JAVA_HOME or add javaw.exe to PATH.
    pause
    exit /b 1
)
start "" "%TETRIS_JAVAW%" -jar "%~dp0out\tetris.jar"
