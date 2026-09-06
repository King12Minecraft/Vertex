@echo off
REM Vertex - Client Launcher
REM Hands off to Vertex.vbs, which runs "java -jar VertexClient.jar"
REM with its console window hidden - this only needs plain "java" to
REM work (same as Run-VertexClient-LowEnd.bat), not "javaw", which
REM isn't available on every setup.
REM No console to show errors in - use Run-VertexClient-LowEnd.bat
REM instead if something's going wrong and you need to see why.
start "" wscript.exe "%~dp0Vertex.vbs"
