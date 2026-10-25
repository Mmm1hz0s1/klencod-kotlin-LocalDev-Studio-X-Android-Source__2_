#!/usr/bin/env bash
set -e
APP_HOME=$( cd "${0%/*}" && pwd -P )
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# If gradle-wrapper.jar exists, use it directly
if [ -f "$WRAPPER_JAR" ]; then
    exec java -Xmx64m -Xms64m -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

# Otherwise, if system gradle is installed on the GitHub Actions runner, use it
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Otherwise download Gradle 8.9 binary distribution on the CI runner
GRADLE_DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-8.9-bin"
if [ ! -x "$GRADLE_DIST_DIR/gradle-8.9/bin/gradle" ]; then
    mkdir -p "$GRADLE_DIST_DIR"
    curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip -o "$GRADLE_DIST_DIR/gradle.zip"
    unzip -q -o "$GRADLE_DIST_DIR/gradle.zip" -d "$GRADLE_DIST_DIR"
    chmod +x "$GRADLE_DIST_DIR/gradle-8.9/bin/gradle"
fi
exec "$GRADLE_DIST_DIR/gradle-8.9/bin/gradle" "$@"
