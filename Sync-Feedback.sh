#!/bin/bash
# Vertex - Feedback/Suggestions Sync
# Run this on the machine actually running VertexServer, any time before
# asking for a bug fix or to look at what's been suggested. Commits and
# pushes ONLY gamehub_feedback.txt and gamehub_gamesuggestions.txt - the
# two files .gitignore explicitly excepts from the "server data stays
# local" rule, since these exist specifically to be read by a developer.
# Everything else (accounts, friends, bans, replays, ratings, admin log)
# is untouched and never leaves this machine.
cd "$(dirname "$0")" || exit 1

CHANGED=0
for FILE in gamehub_feedback.txt gamehub_gamesuggestions.txt; do
    if [ -f "$FILE" ]; then
        git add -- "$FILE"
        CHANGED=1
    fi
done

if [ "$CHANGED" -eq 0 ]; then
    echo "No feedback/suggestions files found yet - nothing to sync."
    exit 0
fi

if git diff --cached --quiet; then
    echo "No new feedback or suggestions since the last sync."
    exit 0
fi

git commit -m "Sync feedback/suggestions ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
git push
