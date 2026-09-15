#!/bin/bash
# Genera reservas de prueba: crea y confirma. Cada una emite eventos a Kafka
# que alimentan audit (linea de tiempo) y report (KPIs).
#
# Correr en ec2-apps:
#   bash seed-reservations.sh
#
# Requiere que esten arriba: reservations (8081), catalog (8082), Kafka, audit, report.

set -u
RES="http://localhost:8081"
CAT="http://localhost:8082"

echo "== Obteniendo unidades del catalogo =="
UNIT_IDS=$(curl -s "$CAT/api/units" | grep -o '"id":[0-9]*' | grep -o '[0-9]*' | head -10)

if [ -z "$UNIT_IDS" ]; then
  echo "No se obtuvieron unidades. Verifica que catalog este arriba y el catalogo poblado."
  exit 1
fi

names=("Ana Soto" "Beto Diaz" "Carla Ruiz" "Diego Pino" "Elena Vera" \
       "Fabian Rojas" "Gina Lara" "Hugo Mena" "Ivo Salas" "Julia Toro")

i=0
creadas=0
confirmadas=0
for uid in $UNIT_IDS; do
  gname="${names[$i]}"
  gid="u-$((100+i))"
  day=$(( i % 9 + 1 ))
  cin="2026-10-0${day}"
  cout="2026-10-1${day}"

  # 1. Crear (queda en CREADA) -> emite reservation.created
  resp=$(curl -s -X POST "$RES/api/reservations" -H "Content-Type: application/json" \
    -d "{\"guestId\":\"$gid\",\"guestName\":\"$gname\",\"unitId\":$uid,\"checkInDate\":\"$cin\",\"checkOutDate\":\"$cout\"}")
  rid=$(echo "$resp" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

  if [ -z "$rid" ]; then
    echo "  [!] no se pudo crear (unidad $uid): $resp"
    i=$((i+1)); continue
  fi
  creadas=$((creadas+1))
  echo "  Reserva $rid creada  (huesped: $gname, unidad: $uid)"

  # 2. Confirmar -> baja cupo en catalog y emite reservation.status_changed
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$RES/api/reservations/$rid/status" \
    -H "Content-Type: application/json" -d '{"status":"CONFIRMADA"}')
  if [ "$code" = "200" ]; then
    confirmadas=$((confirmadas+1))
    echo "     -> CONFIRMADA (200)"
  else
    echo "     -> confirmar devolvio HTTP $code (revisar cupo/estado)"
  fi

  i=$((i+1))
done

echo ""
echo "== Resumen: $creadas creadas, $confirmadas confirmadas =="
echo "Espera unos segundos a que Kafka propague y verifica audit/report."
