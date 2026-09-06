@echo off
REM Vertex - Client Launcher
REM Uses javaw instead of java, so double-clicking this opens straight
REM into the app with no visible console window behind it - the last
REM thing standing between this and "just a normal application" was
REM that black terminal window every java -jar launch used to pop up.
REM Errors won't show here since there's no console to print them to -
REM use Run-VertexClient-LowEnd.bat instead if something's going wrong
REM and you need to see the actual error message.
start "" javaw -jar VertexClient.jar
