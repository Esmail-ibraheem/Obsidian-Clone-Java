#!/usr/bin/env bash
# Build and run the production single-image stack in WSL Docker.
#
# Why this script (instead of a plain multi-stage Dockerfile): this environment's
# Docker daemon can't resolve DNS during `docker build` RUN steps, so dependency
# downloads (npm, Gradle) are done here in `docker run --dns` steps which DO have
# working DNS. The resulting jar (with the React bundle inside) is then packaged
# by backend/Dockerfile via a network-free COPY.
#
# Run from the repo root in WSL:
#   MSYS_NO_PATHCONV=1 wsl bash /mnt/f/Obsidian/build-prod.sh   (from Git Bash)
#   ./build-prod.sh                                             (from WSL)
set -euo pipefail
cd "$(dirname "$0")"

echo "==> [1/4] Building the frontend bundle"
docker run --rm --dns 1.1.1.1 -v "$PWD/frontend":/app -w /app node:20-alpine \
  sh -c "npm ci --no-audit --no-fund && npm run build"

echo "==> [2/4] Baking the bundle into backend static resources"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/. backend/src/main/resources/static/

echo "==> [3/4] Building the backend jar (app.jar)"
docker run --rm --dns 1.1.1.1 \
  -v "$PWD/backend":/app -w /app -u root \
  -v obsidian_gradle-cache:/home/gradle/.gradle \
  gradle:8.10.2-jdk21 gradle --no-daemon clean bootJar -x test

echo "==> [4/4] Building + starting the production image"
docker compose up --build -d

echo
echo "Production app: http://localhost:8080"
