#!/bin/sh

# Gradle wrapper script
# This script downloads and runs Gradle based on gradle-wrapper.properties

APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options here
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Determine the Java command to use
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Attempt to set APP_HOME
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# Download URL from properties file
WRAPPER_URL=$(grep "distributionUrl" "$WRAPPER_PROPERTIES" | sed 's/distributionUrl=//' | sed 's/\\:/:/g')
WRAPPER_DIST=$(grep "distributionPath" "$WRAPPER_PROPERTIES" 2>/dev/null | sed 's/distributionPath=//' || echo "wrapper/dists")
WRAPPER_BASE=$(grep "distributionBase" "$WRAPPER_PROPERTIES" 2>/dev/null | sed 's/distributionBase=//' || echo "GRADLE_USER_HOME")

if [ "$WRAPPER_BASE" = "GRADLE_USER_HOME" ]; then
    GRADLE_USER_HOME="${GRADLE_HOME:-$HOME/.gradle}"
else
    GRADLE_USER_HOME="$WRAPPER_BASE"
fi

GRADLE_DIST_DIR="$GRADLE_USER_HOME/$WRAPPER_DIST"
GRADLE_ZIP_NAME=$(echo "$WRAPPER_URL" | sed 's|.*/||')
GRADLE_DIR_NAME=$(echo "$GRADLE_ZIP_NAME" | sed 's/-bin\.zip//')

GRADLE_EXEC="$GRADLE_DIST_DIR/$GRADLE_DIR_NAME/bin/gradle"

# Download Gradle if not exists
if [ ! -f "$GRADLE_EXEC" ]; then
    echo "Downloading $APP_NAME..."
    mkdir -p "$GRADLE_DIST_DIR"
    
    # Create a simple download script
    DOWNLOAD_TMP="$GRADLE_DIST_DIR/download_tmp"
    
    if command -v wget >/dev/null 2>&1; then
        wget -q "$WRAPPER_URL" -O "$GRADLE_DIST_DIR/$GRADLE_ZIP_NAME"
    elif command -v curl >/dev/null 2>&1; then
        curl -sL "$WRAPPER_URL" -o "$GRADLE_DIST_DIR/$GRADLE_ZIP_NAME"
    else
        echo "Error: Neither wget nor curl is available"
        exit 1
    fi
    
    if [ ! -f "$GRADLE_DIST_DIR/$GRADLE_ZIP_NAME" ]; then
        echo "Error: Failed to download Gradle"
        exit 1
    fi
    
    # Extract
    echo "Extracting..."
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$GRADLE_DIST_DIR/$GRADLE_ZIP_NAME" -d "$GRADLE_DIST_DIR"
    else
        echo "Error: unzip is not available"
        exit 1
    fi
    
    rm -f "$GRADLE_DIST_DIR/$GRADLE_ZIP_NAME"
fi

if [ ! -f "$GRADLE_EXEC" ]; then
    echo "Error: Gradle installation failed"
    exit 1
fi

exec "$GRADLE_EXEC" "$@"
