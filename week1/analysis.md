# Week 1 — Architectural Characteristics Analysis
## Self-Checkout Supermarket — Monolithic Implementation

---

## Architectural Picks

**Performance**
- Each of the three primary operations (start transaction, scan item, complete transaction) must complete in under 500 ms at p99 under default load (10 stations, 60 s).
- Under stress load (100 stations, 120 s), p95 must remain under 2 s.
- The full item catalog (2 000 SKUs) must be returned in under 200 ms.

**Data Integrity**
- Stock must never go negative for any SKU.
- For every SKU the invariant `initial_stock − final_stock == total completed-transaction units for that SKU` must hold after every test run, including concurrent stress runs.
- Enforced via a single atomic SQL UPDATE: `SET stock = stock − n WHERE sku = ? AND stock >= n`.

**Scalability**
- The system must handle at least 100 concurrent checkout stations without an error rate exceeding 1 %.
- Throughput must scale with the number of concurrent stations up to the hardware limit of the host machine.

**Reliability**
- In-flight transaction items survive a failed or retried complete attempt; no phantom stock decrements occur on a retried call.
- A completed transaction cannot be re-completed (the server returns 409 on a duplicate call).

**Modularity**
- Each concern (catalog, transactions, inventory, analytics) has separate controller, service, and repository classes within one deployable application.
- HTTP changes stay in controllers and DTOs; persistence changes stay in repositories and models where practical. JPA transaction and locking semantics are an explicit dependency of the services.

**Observability**
- Low-stock alerts are persisted to the database and queryable at any time via `/inventory/low-stock`.
- The top-10 popular items from the current hopping window are persisted to the database every 500 scans and queryable via `/analytics/popular-items`.

---

## Prioritized

### 1. Data Integrity

**Requirement:** No SKU's stock goes negative; the decrement invariant holds under concurrent load.

**Implementation decision:** Scanning and completion first take a database write lock on the transaction row, serializing requests for the same basket. Every stock decrement uses a single atomic SQL UPDATE with a `stock >= quantity` guard. A failed decrement raises a conflict and rolls back the whole completion. SKUs are sorted alphabetically so competing completions acquire inventory locks in the same order. Together these decisions prevent lost scan totals, duplicate completion, negative stock, and partial inventory updates.

**Trade-off:** Row-level locking on the inventory table serialises competing decrements for the same SKU. Under stress load with 100 stations and Zipf-skewed item popularity, the most popular SKUs become a contention hotspot, increasing p99 latency on complete-transaction. This is an honest trade: correctness is not negotiable; latency can be improved in later weeks with optimistic concurrency or event-sourced inventory.

---

### 2. Performance

**Requirement:** p99 complete-transaction under 500 ms at default load; p95 under 2 s at stress load.

**Implementation decision:** Spring Boot 3.3 uses Java 21 virtual request threads, while the database connection pool remains limited to 30 connections. The item catalog is cached after its first request. Only committed scans enter the analytics deque. The counter, deque update, window copy, and task submission share one lock; one background worker persists snapshots in submission order through `AnalyticsSnapshotService`, with a database transaction for each snapshot. Each scan still reads the transaction and inventory, inserts its item, and updates the basket.

**Trade-off:** Database-backed baskets and row locks add work to each request. Background analytics keeps snapshot writes off the scan response path, but the analytics GET waits for the latest submitted snapshot so the client receives current persisted results. Reset also waits for outstanding snapshot writes. The in-memory scan window does not survive an application restart; the latest persisted snapshot does. Virtual threads do not remove database contention or guarantee one carrier thread can serve every blocked request.

---

### 3. Modularity

**Requirement:** Keep catalog, checkout, inventory, and analytics responsibilities in separate classes with explicit dependencies, while deploying them as one monolithic application.

**Implementation decision:** Controllers delegate to services; services delegate to repositories. `AdminController` is the deliberate exception: it coordinates reset through repositories and the analytics service. Checkout calls analytics after a scan commits, and analytics delegates background persistence to `AnalyticsSnapshotService`. These are ordinary in-process calls within one Spring Boot executable, not independently deployed services. Plain SQL scripts initialize the PostgreSQL schema and seed data.

**Trade-off:** The layered structure adds boilerplate — interfaces, DTOs, repository classes — that a single-class monolith would not need. This slightly increases initial code volume for week 1, in exchange for predictability in weeks 2–6 when the architecture changes but the business logic does not.

---

## Load Test Results

### Default run — 10 stations, 60 s

Report: [report-20260918-153153.json](reports/report-20260918-153153.json).
The server used port 8081 and an isolated PostgreSQL database with the same
configuration and seed data as the normal port-8080 deployment.

| Operation            |   OK | Errors | Mean ms | p50 ms | p95 ms | p99 ms |
|----------------------|------|--------|---------|--------|--------|--------|
| START_TRANSACTION    | 4362 |      0 |    7.4  |    6.4 |   12.1 |   15.9 |
| SCAN_ITEM            |46346 |      0 |   10.1  |    9.4 |   14.7 |   19.7 |
| COMPLETE_TRANSACTION | 4362 |      0 |   23.4  |   19.2 |   50.4 |   75.3 |

Throughput: **72.5 transactions/sec**, **770.5 items/sec** — 0 % error rate.
The default p99 latency target was met for all three operations. Database checks
found zero negative stocks, zero stock-accounting mismatches, and zero basket
count/total mismatches. The persisted analytics window ended at scan 46,000,
the last completed 500-scan boundary before the total of 46,346 scans.

### Stress run — 100 stations, 120 s

Report: [report-20260918-154250.json](reports/report-20260918-154250.json).
Inventory and analytics were reset before this run. Actual elapsed time was
120.66 seconds, including completion of in-flight work.

| Operation            |   OK | Errors | Mean ms | p50 ms |  p95 ms |  p99 ms |
|----------------------|------|--------|---------|--------|---------|---------|
| START_TRANSACTION    |14824 |      0 |   55.7  |   55.1 |    79.0 |    95.1 |
| SCAN_ITEM            |155837|      0 |   57.5  |   56.8 |    80.1 |    94.9 |
| COMPLETE_TRANSACTION |10071 |   4753 |  192.6  |   87.9 |   800.1 | 1 700.3 |

Throughput: **83.5 completed transactions/sec**, **1,291.5 scans/sec**.
The client measures latency percentiles for successful requests. The stress p95
target of 2 seconds was met for all three operations, but the scalability target
of less than 1 % errors was **not met**: completion errors were **32.06 %**.

All 4,753 completion errors were HTTP 409 insufficient-stock conflicts for
`SKU-001186`, whose starting inventory of 10,000 units was exhausted. The error
log contained no request timeouts or HTTP 500 responses. The unmodified client
continues choosing sold-out items because scans are not inventory reservations.
Returning successful receipts for those baskets would violate the stock
invariant. Failed completions roll back every inventory change and leave their
baskets open, so Data Integrity takes priority over accepting every sale.

Database checks found **zero negative stocks, zero stock-accounting mismatches,
and zero basket count/total mismatches**. There were 10,071 completed and 4,753
open transactions. The latest 10 persisted rankings covered scans 154,501 through
155,500, the last complete window before the total of 155,837 scans. The low-stock
endpoint returned the sold-out SKU with its persisted trigger timestamp.

An earlier diagnostic stress attempt was repeated after it took 320 seconds and
the database pool logged a 3-minute-49-second thread-starvation/clock-leap warning.
Its report is retained in local working notes; the two submission reports above
are the default run and the subsequent 120.66-second stress run.

## Correctness checks

- Concurrent completion: one success and 19 conflicts; stock decremented once.
- Concurrent scans: 20 accepted scans produced quantity 20 and the correct total.
- Insufficient stock: HTTP 409, an open basket, and no partial inventory decrement.
- Missing station ID and malformed JSON: HTTP 400 with the API error response.
- A deferred database failure rolled back a scan without adding it to analytics.
- Concurrent 500-scan waves produced exact 1,000/500 window counts and boundaries.
- Scans completed while the analytics table was locked; querying the latest
  window waited for its persistence. Reset drained queued writes before clearing
  the database and in-memory window.
