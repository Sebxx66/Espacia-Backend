#!/bin/sh
# Prueba de punta a punta de los respaldos (la ejecuta la integración continua contra un PostgreSQL real):
# crea datos, respalda, restaura en otra base y compara; luego prueba la retención y la restauración completa.
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)"
export BACKUP_DIR="${BACKUP_DIR:-$(mktemp -d)}"
export BACKUP_KEEP_DAYS=14
C="-h ${DB_HOST:-localhost} -p ${DB_PORT:-5432} -U ${DB_USERNAME:-postgres}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"
DB="${DB_NAME:-bibliospace_db}"
q() { psql $C -d "$1" -tAc "$2"; }
falla() { echo "FALLA: $*" >&2; exit 1; }

q "$DB" "drop table if exists prueba_respaldo; create table prueba_respaldo(id serial primary key, nombre text not null);
         insert into prueba_respaldo(nombre) select 'fila ' || g from generate_series(1, 500) g;"

sh "$DIR/respaldar.sh"
n=$(ls "$BACKUP_DIR"/espacia-*.dump | wc -l)
[ "$n" -eq 1 ] || falla "se esperaba 1 respaldo y hay $n"

sh "$DIR/respaldar.sh" --uno-por-dia
[ "$(ls "$BACKUP_DIR"/espacia-*.dump | wc -l)" -eq 1 ] || falla "--uno-por-dia hizo un segundo respaldo"

archivo=$(ls "$BACKUP_DIR"/espacia-*.dump | head -1)
psql $C -d postgres -qc "drop database if exists prueba_restauracion"
sh "$DIR/restaurar.sh" "$archivo" prueba_restauracion
[ "$(q prueba_restauracion 'select count(*) from prueba_respaldo')" = "500" ] || falla "la base restaurada no tiene las 500 filas"
sh "$DIR/restaurar.sh" "$archivo" prueba_restauracion 2>/dev/null && falla "restaurar sobre una base existente debía fallar"

# Retención: un respaldo de hace 20 días se borra; uno de hace 3 días se conserva
touch -t "$(date -d '@'$(( $(date +%s) - 20 * 86400 )) +%Y%m%d%H%M 2>/dev/null || date -r $(( $(date +%s) - 20 * 86400 )) +%Y%m%d%H%M)" "$BACKUP_DIR/espacia-2000-01-01_000000.dump"
touch -t "$(date -d '@'$(( $(date +%s) - 3 * 86400 )) +%Y%m%d%H%M 2>/dev/null || date -r $(( $(date +%s) - 3 * 86400 )) +%Y%m%d%H%M)" "$BACKUP_DIR/espacia-2000-01-02_000000.dump"
sleep 1
sh "$DIR/respaldar.sh"
[ ! -f "$BACKUP_DIR/espacia-2000-01-01_000000.dump" ] || falla "no se borró el respaldo viejo"
[ -f "$BACKUP_DIR/espacia-2000-01-02_000000.dump" ] || falla "se borró un respaldo reciente"
rm -f "$BACKUP_DIR/espacia-2000-01-02_000000.dump"

# Restauración completa: un cambio posterior al respaldo se deshace y queda un respaldo previo de seguridad
q "$DB" "delete from prueba_respaldo where id > 100"
antes=$(ls "$BACKUP_DIR"/espacia-*.dump | wc -l)
sh "$DIR/restaurar.sh" "$archivo" --si
[ "$(q "$DB" 'select count(*) from prueba_respaldo')" = "500" ] || falla "la restauración completa no recuperó las filas"
[ "$(ls "$BACKUP_DIR"/espacia-*.dump | wc -l)" -eq $(( antes + 1 )) ] || falla "no se hizo el respaldo previo a restaurar"
grep -q "^.* OK espacia-" "$BACKUP_DIR/respaldos.log" || falla "el log no registra los respaldos"

q "$DB" "drop table prueba_respaldo"
psql $C -d postgres -qc "drop database prueba_restauracion"
echo "Respaldos: todas las pruebas pasaron."
