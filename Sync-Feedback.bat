@echo off
REM Vertex - Feedback/Suggestions Sync
REM Run this on the machine actually running VertexServer, any time
REM before asking for a bug fix or to look at what's been suggested.
REM Commits and pushes ONLY gamehub_feedback.txt and
REM gamehub_gamesuggestions.txt - the two files .gitignore explicitly
REM excepts from the "server data stays local" rule, since these exist
REM specifically to be read by a developer. Everything else (accounts,
REM friends, bans, replays, ratings, admin log) is untouched and never
REM leaves this machine.
cd /d "%~dp0"

set "CHANGED=0"
if exist "gamehub_feedback.txt" (
    git add -- gamehub_feedback.txt
    set "CHANGED=1"
)
if exist "gamehub_gamesuggestions.txt" (
    git add -- gamehub_gamesuggestions.txt
    set "CHANGED=1"
)

if "%CHANGED%"=="0" (
    echo No feedback/suggestions files found yet - nothing to sync.
    pause
    exit /b 0
)

git diff --cached --quiet
if not errorlevel 1 (
    echo No new feedback or suggestions since the last sync.
    pause
    exit /b 0
)

git commit -m "Sync feedback/suggestions"
git push
pause
