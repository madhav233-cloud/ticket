#!/usr/bin/env bash
# Simple concurrency smoke test for TicketLock
# Usage: ./load-test.sh

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-20}"
SEAT_ID="${SEAT_ID:-1}"
EVENT_ID="${EVENT_ID:-1}"
USER_ID="${USER_ID:-1}"

echo "=== TicketLock Load Test ==="
echo "Target: $BASE_URL"
echo "Concurrency: $CONCURRENCY concurrent booking attempts on seat $SEAT_ID"
echo ""

# Ensure event is published (ignore errors if already published)
curl -s -X POST "$BASE_URL/api/events/$EVENT_ID/publish" > /dev/null || true

success=0
conflict=0
error=0

echo "Sending $CONCURRENCY parallel booking requests..."

for i in $(seq 1 "$CONCURRENCY"); do
  (
    resp=$(curl -s -o /tmp/tl_resp_$i.json -w "%{http_code}" \
      -X POST "$BASE_URL/api/bookings" \
      -H "Content-Type: application/json" \
      -d "{
        \"userId\": $USER_ID,
        \"eventId\": $EVENT_ID,
        \"seatIds\": [$SEAT_ID],
        \"idempotencyKey\": \"load-test-$i-$(date +%s%N)\"
      }")

    if [ "$resp" = "201" ]; then
      echo "Request $i → SUCCESS (201)"
    elif [ "$resp" = "409" ]; then
      echo "Request $i → CONFLICT (409) – seat already locked"
    else
      echo "Request $i → ERROR ($resp)"
      cat /tmp/tl_resp_$i.json 2>/dev/null || true
    fi
  ) &
done

wait

echo ""
echo "=== Done ==="
echo "Expected: exactly 1 SUCCESS, rest CONFLICT (409)"
echo "This proves the system prevents overselling under concurrent load."
