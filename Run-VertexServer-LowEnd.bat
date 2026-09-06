@echo off
REM Vertex - Server Launcher (low-end friendly)
REM See Run-VertexClient-LowEnd.bat for why each JVM flag is here, and
REM Vertex.bat for why this looks for java.exe itself instead of
REM assuming it's on PATH.
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
"!FOUND!" -Xmx192m -XX:+UseSerialGC -Xss256k -jar VertexServer.jar
if errorlevel 1 (
    echo.
    echo Could not find or run Java. Is a JDK installed anywhere on this computer?
)
pause
