#!/usr/bin/env sh

JDK_DIR="$(dirname "$0")/.jdk-17"
if [ ! -d "$JDK_DIR" ]; then
    echo "Setting up JDK 17 for Gradle compatibility..."
    mkdir -p "$JDK_DIR"
    JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.14%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.14_7.tar.gz"
    curl -sS -L "$JDK_URL" | tar -xz -C "$JDK_DIR" --strip-components=1 || {
        echo "Failed to download JDK 17"
        rm -rf "$JDK_DIR"
        exit 1
    }
fi

export JAVA_HOME="$(cd "$JDK_DIR" && pwd)/Contents/Home"
JAVACMD="$JAVA_HOME/bin/java"

WRAPPER_JAR="$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p "$(dirname "$WRAPPER_JAR")"
    curl -sS -L -o "$WRAPPER_JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar" || {
        echo "Failed to download gradle-wrapper.jar"
        exit 1
    }
fi

exec "$JAVACMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
