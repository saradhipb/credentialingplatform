#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build.sh
#
# Builds the Angular client, copies the bundle into the Spring Boot service's
# static resources, then builds and tests the service. The result is a single
# executable JAR that serves both the API and the user interface.
#
# Usage:  ./build.sh          build and test
#         ./build.sh run      build, test, then start the service
# ---------------------------------------------------------------------------
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Building the Angular client"
cd "$ROOT/frontend"
npm install
npm run build

echo "==> Copying the client bundle into the service's static resources"
rm -rf "$ROOT/backend/src/main/resources/static"
mkdir -p "$ROOT/backend/src/main/resources/static"
cp -r "$ROOT/frontend/dist/." "$ROOT/backend/src/main/resources/static/"

echo "==> Building and testing the Spring Boot service"
cd "$ROOT/backend"
mvn clean package

echo "==> Build complete: backend/target/credentialing-platform.jar"

if [[ "${1:-}" == "run" ]]; then
  echo "==> Starting the platform on http://localhost:8080"
  java -jar target/credentialing-platform.jar
fi
