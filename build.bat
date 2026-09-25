@echo off
REM Vertex - Build Script
REM Compiles VertexClient and VertexServer from source and packages each
REM into its runnable jar (VertexClient.jar / VertexServer.jar) at the
REM repo root - the same jars the README tells people to run directly
REM with `java -jar`. This replaces BlueJ's own "Create Application"
REM export as the way those get built; BlueJ still opens/compiles/runs
REM either project fine on its own (see the package.bluej files in each
REM folder) - this is just an additional, repeatable, one-command path
REM that doesn't require BlueJ at all. Looks for a JDK the same way the
REM Run-*.bat launchers do, so there's nothing to configure by hand.
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "FOUND="
if exist "%ProgramFiles%\BlueJ\jdk\bin\javac.exe" set "FOUND=%ProgramFiles%\BlueJ\jdk\bin"
if not defined FOUND if exist "%ProgramFiles(x86)%\BlueJ\jdk\bin\javac.exe" set "FOUND=%ProgramFiles(x86)%\BlueJ\jdk\bin"
if not defined FOUND if exist "%LocalAppData%\Programs\BlueJ\jdk\bin\javac.exe" set "FOUND=%LocalAppData%\Programs\BlueJ\jdk\bin"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Java\jdk*")             do if exist "%%D\bin\javac.exe" set "FOUND=%%D\bin"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk*") do if exist "%%D\bin\javac.exe" set "FOUND=%%D\bin"
if not defined FOUND for /d %%D in ("%ProgramFiles%\AdoptOpenJDK\jdk*")     do if exist "%%D\bin\javac.exe" set "FOUND=%%D\bin"
if not defined FOUND for /d %%D in ("%ProgramFiles%\Microsoft\jdk*")       do if exist "%%D\bin\javac.exe" set "FOUND=%%D\bin"
if not defined FOUND set "FOUND=."

set "JAVAC=!FOUND!\javac.exe"
set "JARTOOL=!FOUND!\jar.exe"

set "VERSION="
if exist VERSION set /p VERSION=<VERSION
if not defined VERSION set "VERSION=0.0.0-dev"

echo Building Vertex !VERSION!
echo Using JDK at: !FOUND!

echo.
echo --- Building VertexClient.jar ---
if exist build_client rmdir /s /q build_client
mkdir build_client
dir /s /b VertexClient\*.java > sources_client.txt
"!JAVAC!" -d build_client @sources_client.txt
if errorlevel 1 goto :error

(
    echo Main-Class: Vertex
    echo Implementation-Version: !VERSION!
) > manifest_client.txt
"!JARTOOL!" cfm VertexClient.jar manifest_client.txt -C build_client .

echo.
echo --- Building VertexServer.jar ---
if exist build_server rmdir /s /q build_server
mkdir build_server
dir /s /b VertexServer\*.java > sources_server.txt
"!JAVAC!" -d build_server @sources_server.txt
if errorlevel 1 goto :error

(
    echo Main-Class: ServerMain
    echo Implementation-Version: !VERSION!
) > manifest_server.txt
"!JARTOOL!" cfm VertexServer.jar manifest_server.txt -C build_server .

del /q sources_client.txt sources_server.txt manifest_client.txt manifest_server.txt
rmdir /s /q build_client build_server

echo.
echo Done. VertexClient.jar and VertexServer.jar are ready.
pause
exit /b 0

:error
echo.
echo Build failed - see the errors above.
pause
exit /b 1
