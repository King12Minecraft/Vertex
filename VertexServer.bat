@echo off
REM Vertex - Server Launcher
REM See Vertex.bat for why this goes through VertexServer.vbs.
REM No console to show errors in - use Run-VertexServer-LowEnd.bat
REM instead if something's going wrong and you need to see why.
start "" wscript.exe "%~dp0VertexServer.vbs"
