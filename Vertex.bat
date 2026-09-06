@echo off
REM Vertex - Client Launcher
REM Doesn't rely on java being on PATH (most people's PATH doesn't
REM include it, including BlueJ-only setups) - instead checks the
REM handful of places a JDK usually ends up on Windows, in order,
REM and uses the first one it finds. Falls back to plain "java" in
REM case PATH does happen to have it. No setup needed on any machine
REM this is copied to.
setlocal enabledelayedexpansion
set "FOUND="

if exist "%ProgramFiles%\BlueJ\jdk\bin\java.exe" set "FOUND=%ProgramFiles%\BlueJ\jdk\bin\java.exe"
if not defined FOUND if exist "%ProgramFiles(x86)%\BlueJ\jdk\bin\java.exe" set "FOUND=%ProgramFiles(x86)%\BlueJ\jdk\bin\java.exe"
if not defined FOUND if exist "%LocalAppData%\Programs\BlueJ\jdk\bin\java.exe" set "FOUND=%LocalAppData%\Programs\BlueJ\jdk\bin\java.exe"

if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\jdk*")             do if exist "%%D\bin\java.exe" set "FOUND=%%D\bin\java.exe"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk*") do if exist "%%D\bin\java.exe" set "FOUND=%%D\bin\java.exe"
if not defined FOUND for /d %%D in ("%ProgramFiles%\AdoptOpenJDK\jdk*")     do if exist "%%D\bin\java.exe" set "FOUND=%%D\bin\java.exe"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Microsoft\jdk*")       do if exist "%%D\bin\java.exe" set "FOUND=%%D\bin\java.exe"

if not defined FOUND set "FOUND=java.exe"

echo Using Java at: !FOUND!
"!FOUND!" -jar VertexClient.jar
if errorlevel 1 (
    echo.
    echo Could not find or run Java. Is a JDK installed anywhere on this computer?
)
pause
