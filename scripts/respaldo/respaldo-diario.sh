#!/bin/sh
# Servicio de respaldo de docker-compose: respalda al arrancar (si aún no hay uno de hoy)
# y luego todos los días a la hora BACKUP_TIME (HH:MM, por defecto 03:00, en la zona TZ).
set -u
BACKUP_TIME="${BACKUP_TIME:-03:00}"
DIR="$(dirname "$0")"

sh "$DIR/respaldar.sh" --uno-por-dia || echo "El respaldo inicial falló; se reintenta a la hora programada."

hora=$(echo "$BACKUP_TIME" | cut -d: -f1 | sed 's/^0*//')
minuto=$(echo "$BACKUP_TIME" | cut -d: -f2 | sed 's/^0*//')
objetivo=$(( ${hora:-0} * 3600 + ${minuto:-0} * 60 ))

while true; do
  ahora=$(( $(date +%H | sed 's/^0*//;s/^$/0/') * 3600 + $(date +%M | sed 's/^0*//;s/^$/0/') * 60 + $(date +%S | sed 's/^0*//;s/^$/0/') ))
  espera=$(( objetivo - ahora ))
  [ "$espera" -le 0 ] && espera=$(( espera + 86400 ))
  echo "Próximo respaldo en $(( espera / 3600 )) h $(( espera % 3600 / 60 )) min ($BACKUP_TIME)."
  sleep "$espera"
  sh "$DIR/respaldar.sh" || echo "El respaldo falló; se reintenta mañana a la misma hora."
done
