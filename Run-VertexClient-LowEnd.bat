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
java -Xmx192m -XX:+UseSerialGC -Xss256k -jar VertexClient.jar
pause
