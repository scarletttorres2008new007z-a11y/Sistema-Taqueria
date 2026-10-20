#!/usr/bin/env bash
# Respaldo de la base de datos y de los archivos subidos (Linux/macOS).
set -e
cd "$(dirname "$0")"
set -a; [ -f .env ] && . ./.env; set +a
FECHA=$(date +%Y%m%d-%H%M)
mkdir -p respaldos
PGPASSWORD="$DB_PASSWORD" pg_dump -h localhost -U "${DB_USER:-taqueria}" -F c -f "respaldos/taqueria-$FECHA.dump" taqueria
if [ -d "${UPLOADS_DIR:-uploads}" ]; then tar -czf "respaldos/archivos-$FECHA.tar.gz" -C "${UPLOADS_DIR:-uploads}" .; fi
echo "Respaldo guardado: respaldos/taqueria-$FECHA.dump"
