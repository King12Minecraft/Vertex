#!/bin/bash
# Vertex - Build Script
# Compiles VertexClient and VertexServer from source and packages each
# into its runnable jar (VertexClient.jar / VertexServer.jar) at the
# repo root - the same jars the README tells people to run directly
# with `java -jar`. This replaces BlueJ's own "Create Application"
# export as the way those get built; BlueJ still opens/compiles/runs
# either project fine on its own (see the package.bluej files in each
# folder) - this is just an additional, repeatable, one-command path
# that doesn't require BlueJ at all.
#
# Always compiles into a fresh temp directory, so there's no stale
# .class file left behind from a previous build to worry about.
set -e
cd "$(dirname "$0")"

VERSION=$(cat VERSION 2>/dev/null || echo "0.0.0-dev")
BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo "Building Vertex $VERSION ($BUILD_DATE)"

build_one() {
    local SRC_DIR="$1"
    local MAIN_CLASS="$2"
    local JAR_NAME="$3"

    echo ""
    echo "--- Building $JAR_NAME from $SRC_DIR ---"
    local OUT_DIR
    OUT_DIR=$(mktemp -d)

    find "$SRC_DIR" -name "*.java" > "$OUT_DIR/sources.txt"
    javac -d "$OUT_DIR" @"$OUT_DIR/sources.txt"

    local MANIFEST
    MANIFEST=$(mktemp)
    {
        echo "Main-Class: $MAIN_CLASS"
        echo "Implementation-Version: $VERSION"
        echo "Implementation-Build-Date: $BUILD_DATE"
    } > "$MANIFEST"

    jar cfm "$JAR_NAME" "$MANIFEST" -C "$OUT_DIR" .

    rm -rf "$OUT_DIR" "$MANIFEST"
    echo "Built $JAR_NAME"
}

build_one VertexClient Vertex VertexClient.jar
build_one VertexServer ServerMain VertexServer.jar

echo ""
echo "Done. VertexClient.jar and VertexServer.jar are ready at the repo root."
