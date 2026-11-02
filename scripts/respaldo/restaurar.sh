#!/bin/sh
# Restaura un respaldo de Espacia.
#
# Uso:
#   sh scripts/respaldo/restaurar.sh ARCHIVO.dump BASE_NUEVA   -> lo carga en una base nueva (para revisarlo o probarlo)
#   sh scripts/respaldo/restaurar.sh ARCHIVO.dump --si          -> reemplaza la base de Espacia (DB_NAME)
#
# Antes de reemplazar la base hace un respaldo del estado actual, por si se elige el archivo equivocado.
# Detén el backend antes de reemplazar la base. Usa las mismas variables que respaldar.sh.
set -eu

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-bibliospace_db}"
DB_USERNAME="${DB_USERNAME:-postgres}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"

ARCHIVO="${1:-}"
DESTINO="${2:-}"
if [ -z "$ARCHIVO" ] || [ -z "$DESTINO" ]; then
  sed -n '4,6p' "$0" | sed 's/^# //'
  exit 2
fi
if [ ! -f "$ARCHIVO" ]; then
  echo "No existe el archivo $ARCHIVO" >&2
  exit 2
fi
pg_restore --list "$ARCHIVO" >/dev/null || { echo "El archivo no es un respaldo válido de pg_dump" >&2; exit 1; }

CONEXION="-h $DB_HOST -p $DB_PORT -U $DB_USERNAME"

if [ "$DESTINO" = "--si" ]; then
  echo "Respaldando el estado actual antes de restaurar..."
  sh "$(dirname "$0")/respaldar.sh"
  echo "Restaurando $(basename "$ARCHIVO") en $DB_NAME..."
  # Una sola transacción: si algo falla, la base queda como estaba
  # shellcheck disable=SC2086
  pg_restore $CONEXION -d "$DB_NAME" --clean --if-exists --no-owner --no-privileges --single-transaction "$ARCHIVO"
  echo "Listo: $DB_NAME quedó como en $(basename "$ARCHIVO"). Ya puedes iniciar el backend."
  exit 0
fi

case "$DESTINO" in
  -*|*[!A-Za-z0-9_]*) echo "Nombre de base no válido: $DESTINO (usa letras, números y _)" >&2; exit 2 ;;
esac
if [ "$DESTINO" = "$DB_NAME" ]; then
  echo "Para reemplazar la base de Espacia usa --si en lugar de su nombre." >&2
  exit 2
fi
# shellcheck disable=SC2086
if [ "$(psql $CONEXION -d postgres -tAc "select 1 from pg_database where datname = '$DESTINO'")" = "1" ]; then
  echo "La base $DESTINO ya existe. Elige otro nombre o bórrala primero." >&2
  exit 1
fi
# shellcheck disable=SC2086
createdb $CONEXION "$DESTINO"
# shellcheck disable=SC2086
pg_restore $CONEXION -d "$DESTINO" --no-owner --no-privileges --single-transaction "$ARCHIVO"
echo "Listo: $(basename "$ARCHIVO") quedó cargado en la base $DESTINO."
