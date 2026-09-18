# CS6510 — Self-Checkout Supermarket (Semester Project)

Each week reimplements the same self-checkout API in a different architecture style.
The load client and OpenAPI contract never change — only the server architecture does.

Week 1 is one Spring Boot application containing checkout, inventory, catalog,
and analytics. Its controller/service/repository packages and background analytics
worker run in the same process and deploy together. PostgreSQL stores the data
in a separate container; the application remains a monolith.

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
    report-*.json          ← Timestamped default and stress results
```

## Week 1 — Quick start

Requires JDK 21, Maven, Docker, and Git. Run these commands from the repository
root, using a separate terminal for the server. The report filenames and measured
results for the current implementation are listed in `week1/analysis.md`.

### 1. Start the database
```bash
docker compose -f week1/docker-compose.yml up -d
```

### 2. Start the server
```bash
mvn -f week1/java/pom.xml spring-boot:run
```
Server listens on **http://localhost:8080**.

### 3. Build the instructor's load client

Clone the course repository if `CS6510-2026` is not already present:

```bash
git clone https://github.com/gortonator/CS6510-2026.git CS6510-2026
bash CS6510-2026/load-client/build.sh
```

Alternatively, compile in PowerShell:

```powershell
$clientSources = Get-ChildItem CS6510-2026/load-client/src -Filter *.java | Select-Object -ExpandProperty FullName
javac -d CS6510-2026/load-client/out $clientSources
```

### 4. Run the required tests

Stop any previous load run before resetting. In PowerShell, use `curl.exe`
instead of `curl` for these reset commands.

```bash
# Default run
curl -X POST http://localhost:8080/admin/reset
java -cp CS6510-2026/load-client/out Main --reportDir=week1/reports

# Stress run
curl -X POST http://localhost:8080/admin/reset
java -cp CS6510-2026/load-client/out Main --stations=100 --duration=120 --reportDir=week1/reports
```

### 5. Reset between runs
```bash
# Quick reset (no Docker restart needed)
curl -X POST http://localhost:8080/admin/reset

# Full reset (wipes DB volume and reseeds)
bash week1/db/reset.sh
```

The quick reset waits for pending analytics writes before clearing the window
and restoring all 2,000 stock levels to 10,000. Use reset between runs, with
checkout traffic stopped. Insufficient stock returns HTTP 409 and rolls back
the entire completion; payment simulation never permits negative stock.

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
