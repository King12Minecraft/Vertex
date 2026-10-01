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

    # The client shows CHANGELOG.md on its Changelog page (the same file the website reads),
    # so it travels inside the client jar.
    if [ "$SRC_DIR" = "VertexClient" ] && [ -f CHANGELOG.md ]; then
        cp CHANGELOG.md "$OUT_DIR/"
    fi

    jar cfm "$JAR_NAME" "$MANIFEST" -C "$OUT_DIR" .

    rm -rf "$OUT_DIR" "$MANIFEST"
    echo "Built $JAR_NAME"
}

build_one VertexClient Vertex VertexClient.jar
build_one VertexServer ServerMain VertexServer.jar

# ./build.sh --obfuscate  also writes VertexClient-release.jar: the same client with class/method/field names
# scrambled and debug info removed (roadmap item "protect the source code"). It is a speed bump for casual
# copying, not a lock - the server stays the authority. Needs a JDK with jmods/ and a one-time ProGuard
# download (cached in .tools/, checksum-verified). See proguard/vertex-client.pro for what is kept and why.
obfuscate_client() {
    local PG_VERSION="7.6.1"
    local PG_SHA256="ce491ec6ed3a8c03b663db65ea31702acd710d7b0af16e12b81a016da57600d6"
    local PG_JAR=".tools/proguard-$PG_VERSION/lib/proguard.jar"
    local JAVA_HOME_DIR
    JAVA_HOME_DIR=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")

    if [ ! -d "$JAVA_HOME_DIR/jmods" ]; then
        echo "Obfuscation needs a full JDK with a jmods/ folder (looked in $JAVA_HOME_DIR). Skipping." >&2
        return 1
    fi

    if [ ! -f "$PG_JAR" ]; then
        echo "Downloading ProGuard $PG_VERSION (one time, cached in .tools/)..."
        mkdir -p .tools
        curl -fsSL -o ".tools/proguard-$PG_VERSION.zip" \
            "https://github.com/Guardsquare/proguard/releases/download/v$PG_VERSION/proguard-$PG_VERSION.zip"
        local GOT
        GOT=$(sha256sum ".tools/proguard-$PG_VERSION.zip" | cut -d' ' -f1)
        if [ "$GOT" != "$PG_SHA256" ]; then
            echo "ProGuard download failed its checksum (got $GOT) - not using it." >&2
            rm -f ".tools/proguard-$PG_VERSION.zip"
            return 1
        fi
        unzip -q -o ".tools/proguard-$PG_VERSION.zip" -d .tools
    fi

    local LIBS=()
    local JMOD
    for JMOD in "$JAVA_HOME_DIR"/jmods/*.jmod; do
        LIBS+=(-libraryjars "$JMOD(!**.jar;!module-info.class)")
    done

    echo ""
    echo "--- Obfuscating VertexClient.jar -> VertexClient-release.jar ---"
    java -jar "$PG_JAR" @proguard/vertex-client.pro \
        -injars VertexClient.jar -outjars VertexClient-release.jar "${LIBS[@]}" > /dev/null

    # The manifest (Main-Class, version) is not carried over by ProGuard - rebuild it on the output.
    local MANIFEST
    MANIFEST=$(mktemp)
    {
        echo "Main-Class: Vertex"
        echo "Implementation-Version: $VERSION"
        echo "Implementation-Build-Date: $BUILD_DATE"
    } > "$MANIFEST"
    jar ufm VertexClient-release.jar "$MANIFEST"
    rm -f "$MANIFEST"

    # Obfuscation must never change what goes over the wire - verify against the plain jar.
    local CHECK_DIR
    CHECK_DIR=$(mktemp -d)
    javac -d "$CHECK_DIR" proguard/WireCompatCheck.java
    java -cp "$CHECK_DIR" WireCompatCheck VertexClient.jar VertexClient-release.jar
    rm -rf "$CHECK_DIR"
    echo "Built VertexClient-release.jar"
}

if [ "$1" = "--obfuscate" ]; then
    obfuscate_client
fi

echo ""
echo "Done. VertexClient.jar and VertexServer.jar are ready at the repo root."
