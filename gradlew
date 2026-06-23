#!/bin/sh

# Gradle wrapper script
GRADLE_VERSION=8.10
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"

if [ ! -f "$GRADLE_HOME/bin/gradle" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_HOME"
    curl -sL "$GRADLE_URL" -o /tmp/gradle-wrapper.zip
    unzip -q /tmp/gradle-wrapper.zip -d "$GRADLE_HOME/.."
    echo "Gradle downloaded"
fi

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk-17}"
export PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH"
exec gradle "$@"
