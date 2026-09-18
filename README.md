# CS6510 — Self-Checkout Supermarket (Semester Project)

Each week reimplements the same self-checkout API in a different architecture style.
The load client and OpenAPI contract never change — only the server architecture does.

## Repository layout

```
week1/                    ← Week 1: Monolithic implementation
  java/                   ← Spring Boot 3.3 / Java 21 server (port 8080)
  db/
    init.sql              ← Schema (inventory, transactions, transaction_items, popular_items)
    seed.sql              ← 2000 SKUs at 10 000 stock each
    reset.sh              ← reset
  docker-compose.yml      ← PostgreSQL 16 on port 5433
  analysis.md             ← analysis + load-test results
  reports/
    report-*-default.json ← 10 stations, 60 s
    report-*-stress.json  ← 100 stations, 120 s
```

## Week 1 — Quick start

### 1. Start the database
```bash
cd week1
docker compose up -d
```

### 2. Start the server
```bash
cd week1/java
mvn spring-boot:run
```
Server listens on **http://localhost:8080**.

### 3. Run the load client (from the course repo)
```bash
# Default run
java -cp out Main --baseUrl=http://localhost:8080 --stations=10 --duration=60

# Stress run
java -cp out Main --baseUrl=http://localhost:8080 --stations=100 --duration=120
```

### 4. Reset between runs
```bash
# Quick reset (no Docker restart needed)
curl -X POST http://localhost:8080/admin/reset

# Full reset (wipes DB volume and reseeds)
cd week1 && bash db/reset.sh
```

## API endpoints (OpenAPI spec in CS6510-2026/spec/)

| Method | Path | Purpose |
|--------|------|---------|
| GET | /items | Full catalog (fetched once by the client at startup) |
| POST | /transactions | Start a transaction at a station |
| POST | /transactions/{id}/items | Scan one unit of an item |
| POST | /transactions/{id}/complete | Pay and decrement stock |
| GET | /transactions/{id} | Debug / instructor use |
| GET | /inventory/low-stock | Current low-stock alerts |
| GET | /analytics/popular-items | Top-10 in the current sliding window |
| POST | /admin/reset | Reset server state between test runs |
