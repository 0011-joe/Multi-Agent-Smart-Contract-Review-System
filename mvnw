#!/bin/sh
# ---------------------------------------------------------------------------
# Maven Start Wrapper script
# ---------------------------------------------------------------------------

MAVEN_HOME=$HOME/.m2/wrapper/dists/apache-maven-3.9.6-bin
MAVEN_URL="https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"

if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "Downloading Maven to $MAVEN_HOME..."
    mkdir -p "$MAVEN_HOME"
    curl -sL "$MAVEN_URL" -o /tmp/maven-wrapper.zip
    unzip -q /tmp/maven-wrapper.zip -d "$MAVEN_HOME/.."
    echo "Maven downloaded"
fi

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk-17}"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
exec mvn "$@"
