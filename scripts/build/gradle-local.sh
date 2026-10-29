#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
MOAPAN_ROOT=$(pwd -P)
export GRADLE_USER_HOME="$MOAPAN_ROOT/.local/gradle-home"
export TMPDIR="$MOAPAN_ROOT/.local/tmp"
export JAVA_HOME="$MOAPAN_ROOT/.local/jdk-27.0.1.0.2+1/Contents/Home"
export PATH="$MOAPAN_ROOT/.local/bin:$JAVA_HOME/bin:$PATH"
exec ./gradlew -Djava.io.tmpdir="$TMPDIR" -Dorg.gradle.java.installations.paths="$JAVA_HOME" "$@"
