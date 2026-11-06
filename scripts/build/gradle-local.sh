#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
DAMGOX2_ROOT=$(pwd -P)
export GRADLE_USER_HOME="$DAMGOX2_ROOT/.local/gradle-home"
export TMPDIR="$DAMGOX2_ROOT/.local/tmp"
export JAVA_HOME="$DAMGOX2_ROOT/.local/jdk-27.0.1.0.2+1/Contents/Home"
export PATH="$DAMGOX2_ROOT/.local/bin:$JAVA_HOME/bin:$PATH"
exec ./gradlew -Djava.io.tmpdir="$TMPDIR" -Dorg.gradle.java.installations.paths="$JAVA_HOME" "$@"
