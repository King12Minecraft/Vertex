@echo off
REM Vertex - Client Launcher (low-end friendly)
REM -Xmx192m caps heap size - this Swing app doesn't need much, and a
REM   smaller heap means less work for the garbage collector overall.
REM -XX:+UseSerialGC - the default G1 collector assumes a multi-core
REM   machine and dedicates a background thread to itself; on an old
REM   dual-core or single-core PC that background thread actively
REM   competes with the game for CPU time. Serial GC has near-zero
REM   background overhead - the right tradeoff for a small,
REM   short-lived desktop app like this rather than a large server.
REM -Xss256k - smaller per-thread stack size; harmless here since
REM   nothing in Vertex recurses deeply, and it reduces memory
REM   reserved up front for every thread the app spins up.
REM See Vertex.bat for why this looks for java.exe itself instead of
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
"!FOUND!" -Xmx192m -XX:+UseSerialGC -Xss256k -jar VertexClient.jar
if errorlevel 1 (
    echo.
    echo Could not find or run Java. Is a JDK installed anywhere on this computer?
)
pause
