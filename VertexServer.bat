@echo off
REM Vertex - Server Launcher
REM See Vertex.bat for how this finds Java without needing it on PATH.
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
"!FOUND!" -jar VertexServer.jar
if errorlevel 1 (
    echo.
    echo Could not find or run Java. Is a JDK installed anywhere on this computer?
)
pause
