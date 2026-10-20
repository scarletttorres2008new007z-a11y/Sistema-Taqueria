#!/usr/bin/env bash
# Inicia el sistema de la taqueria (Linux/macOS).
set -e
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "No existe el archivo .env. Copie .env.example como .env y escriba la contraseña de la base de datos."
  exit 1
fi
set -a; . ./.env; set +a
if [ ! -f target/taqueria.jar ]; then
  echo "Compilando el sistema por primera vez..."
  ./mvnw -q -DskipTests package
fi
echo "Iniciando el sistema en http://localhost:${PORT:-8080} (Ctrl+C para detener)"
exec java -jar target/taqueria.jar
