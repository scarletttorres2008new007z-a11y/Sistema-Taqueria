#!/usr/bin/env bash
# Ejecuta las pruebas automaticas contra la base taqueria_test (se borra y se vuelve a crear).
set -e
cd "$(dirname "$0")"
set -a; [ -f .env ] && . ./.env; set +a
./mvnw test
