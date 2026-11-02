#!/bin/sh
# Copia de seguridad de la base de datos de Espacia (Linux, macOS o el contenedor de docker-compose).
#
# Uso:  sh scripts/respaldo/respaldar.sh [--uno-por-dia]
#   --uno-por-dia   no hace nada si ya hay un respaldo de hoy (para ejecutarlo al arrancar)
#
# Crea un archivo comprimido con toda la base (formato personalizado de pg_dump), comprueba que
# se puede leer, lo anota en respaldos.log y borra los que tengan más de BACKUP_KEEP_DAYS días.
# Variables (con sus valores por defecto):
#   DB_HOST=localhost  DB_PORT=5432  DB_NAME=bibliospace_db  DB_USERNAME=postgres  DB_PASSWORD
#   BACKUP_DIR=./respaldos  BACKUP_KEEP_DAYS=14
set -eu

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-bibliospace_db}"
DB_USERNAME="${DB_USERNAME:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-./respaldos}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"

case "$BACKUP_KEEP_DAYS" in
  ''|*[!0-9]*|0) echo "BACKUP_KEEP_DAYS debe ser un número mayor que 0" >&2; exit 2 ;;
esac

mkdir -p "$BACKUP_DIR"
LOG="$BACKUP_DIR/respaldos.log"
log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG"; }

if [ "${1:-}" = "--uno-por-dia" ]; then
  for f in "$BACKUP_DIR"/espacia-"$(date +%Y-%m-%d)"_*.dump; do
    if [ -f "$f" ]; then
      echo "Ya hay un respaldo de hoy: $(basename "$f")"
      exit 0
    fi
  done
fi

# Nunca se sobrescribe un respaldo: si ya hay uno con el mismo nombre (mismo segundo), se numera
BASE_NOMBRE="espacia-$(date +%Y-%m-%d_%H%M%S)"
ARCHIVO="$BACKUP_DIR/$BASE_NOMBRE.dump"
n=2
while [ -e "$ARCHIVO" ]; do
  ARCHIVO="$BACKUP_DIR/$BASE_NOMBRE-$n.dump"
  n=$((n + 1))
done
PARCIAL="$ARCHIVO.parcial"

# Se escribe primero en un archivo .parcial: si algo falla, nunca queda un respaldo a medias con nombre válido
if ! pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
    --format=custom --compress=6 --no-owner --no-privileges -f "$PARCIAL" 2>>"$LOG"; then
  rm -f "$PARCIAL"
  log "ERROR: no se pudo respaldar $DB_NAME en $DB_HOST:$DB_PORT (detalle arriba en este log)"
  exit 1
fi
if ! pg_restore --list "$PARCIAL" >/dev/null 2>>"$LOG"; then
  rm -f "$PARCIAL"
  log "ERROR: el respaldo quedó ilegible y se descartó"
  exit 1
fi
mv "$PARCIAL" "$ARCHIVO"
log "OK $(basename "$ARCHIVO") ($(du -h "$ARCHIVO" | cut -f1))"

# Solo después de un respaldo correcto se borran los viejos: siempre queda al menos el de hoy
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'espacia-*.dump' -mtime +"$((BACKUP_KEEP_DAYS - 1))" | while read -r viejo; do
  rm -f "$viejo"
  log "Borrado por tener más de $BACKUP_KEEP_DAYS días: $(basename "$viejo")"
done
