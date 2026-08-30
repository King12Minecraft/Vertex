@echo off
REM Vertex - Server Launcher (low-end friendly)
REM See Run-VertexClient-LowEnd.bat for why each flag is here - same
REM reasoning applies to the server process.
java -Xmx192m -XX:+UseSerialGC -Xss256k -jar VertexServer.jar
pause
