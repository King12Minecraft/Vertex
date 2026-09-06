@echo off
REM Vertex - Client Launcher
REM Plain "java -jar", same as Run-VertexClient-LowEnd.bat but without
REM the low-memory JVM flags - a visible console window stays open
REM behind the game (see README for why the "hidden window" approach
REM was dropped: Windows flags the .vbs trick that used to do this as
REM unsafe). Use Run-VertexClient-LowEnd.bat instead on older/weaker
REM hardware.
java -jar VertexClient.jar
pause
