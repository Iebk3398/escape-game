#!/usr/bin/env bash
# Démo locale : 2 équipes, 4 épreuves + repos, puis finale. Lance http://localhost:8080
set -e
cd "$(dirname "$0")"
( cd frontend && npm ci && npx ng build )
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/frontend/browser/* backend/src/main/resources/static/
cd backend
ESCAPE_DEMO=true mvn -q spring-boot:run
