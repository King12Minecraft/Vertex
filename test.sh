#!/bin/bash
# Vertex - Test Runner
# Compiles VertexServer, compiles every VertexServerTests class against it,
# then runs each test's main() and fails (nonzero exit) if any test reports
# a failure - the real, repeatable "did I break anything" step ROADMAP.md's
# Testing Infrastructure entry asked for, sibling to build.sh.
#
# VertexServerTests is its own top-level sibling directory, not nested
# inside VertexServer - build.sh compiles every .java file under
# VertexServer/ straight into the shipped jar, so a test class living there
# would ship to every user's install. Keeping tests as a sibling (the same
# relationship VertexClient/VertexServer already have to each other) keeps
# them out of the product entirely while still compiling against
# VertexServer's real classes on the classpath.
#
# No external test framework (JUnit etc.) - this project has zero external
# dependencies by design (see build.sh: plain javac + jar, no Maven/Gradle),
# and both trees still open/run directly in BlueJ. Each test's main() uses
# the shared support.Check harness (a label, a boolean, a running total -
# the same pattern every prior scratch test in this project already
# reinvented by hand) and calls System.exit(1) on any failure, so this
# script only needs to check each java process's own exit code.
#
# Several tests exercise flat-file stores that hardcode a relative file
# name (GameSuggestionStore, AdminLog, FeedbackManager, DominionStore) -
# each test class runs from its own fresh temp working directory so those
# files never touch this repo or collide between tests.
set -e
cd "$(dirname "$0")"

echo "Building VertexServer..."
SERVER_OUT=$(mktemp -d)
find VertexServer -name "*.java" > "$SERVER_OUT/sources.txt"
javac -d "$SERVER_OUT" @"$SERVER_OUT/sources.txt"

# NetworkManager is client-only, but one test (net.ResponseTypesTest) checks its RESPONSE_TYPES list
# against what the server actually sends, so that one class is compiled here too (it only needs the
# shared net classes) and put on the test classpath.
CLIENT_NET_OUT=$(mktemp -d)
javac -cp "$SERVER_OUT" -sourcepath VertexClient -d "$CLIENT_NET_OUT" VertexClient/net/NetworkManager.java

echo "Compiling VertexServerTests..."
TEST_OUT=$(mktemp -d)
find VertexServerTests -name "*.java" > "$TEST_OUT/sources.txt"
javac -cp "$SERVER_OUT:$CLIENT_NET_OUT" -d "$TEST_OUT" @"$TEST_OUT/sources.txt"

export VERTEX_REPO_ROOT="$PWD"

OVERALL_STATUS=0
for TEST_SOURCE in $(find VertexServerTests -name "*Test.java" | sort); do
    TEST_CLASS=$(echo "$TEST_SOURCE" | sed 's|^VertexServerTests/||; s|\.java$||; s|/|.|g')
    echo ""
    echo "--- Running $TEST_CLASS ---"
    RUN_DIR=$(mktemp -d)
    if ! (cd "$RUN_DIR" && java -cp "$SERVER_OUT:$CLIENT_NET_OUT:$TEST_OUT" "$TEST_CLASS"); then
        echo "FAILED: $TEST_CLASS"
        OVERALL_STATUS=1
    fi
    rm -rf "$RUN_DIR"
done

rm -rf "$SERVER_OUT" "$TEST_OUT" "$CLIENT_NET_OUT"

echo ""
if [ "$OVERALL_STATUS" -eq 0 ]; then
    echo "All tests passed."
else
    echo "One or more tests FAILED."
fi
exit $OVERALL_STATUS
