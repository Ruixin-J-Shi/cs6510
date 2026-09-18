#!/usr/bin/env bash
# Full DB reset: wipe the volume and re-seed from scratch.
# Run this between test runs to restore 2000 SKUs at 10 000 stock each.
set -e
cd "$(dirname "$0")/.."
echo "[reset] Tearing down containers and wiping volume..."
docker compose down -v
echo "[reset] Starting fresh..."
docker compose up -d
echo "[reset] Waiting for Postgres to be ready..."
until docker compose exec postgres pg_isready -U checkout -d checkout -q -p 5432 2>/dev/null; do
  sleep 1
done
echo "[reset] Done. Database is clean and seeded."
