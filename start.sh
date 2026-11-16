#!/bin/sh
set -e

if [ ! -f ".env" ]; then
  if ! command -v openssl >/dev/null 2>&1; then
    echo "Copy .env.example to .env and set DB_PASSWORD before starting." >&2
    exit 1
  fi
  (
  umask 077
  local_password="$(openssl rand -hex 24)"
  cat > .env << EOF
DB_NAME=personal_finance
DB_URL=jdbc:postgresql://localhost:5432/personal_finance
DB_USERNAME=finance_user
DB_PASSWORD=${local_password}
EOF
  )
fi

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
