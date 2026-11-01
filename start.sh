#!/bin/sh
set -e

if [ -z "${JAVA_HOME-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  if [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
  fi
fi

if command -v orbctl >/dev/null 2>&1; then
  orbctl start >/dev/null 2>&1 || true
fi

docker compose up -d
exec ./mvnw spring-boot:run
