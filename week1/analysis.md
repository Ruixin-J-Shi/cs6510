# Week 1 — Architectural Characteristics Analysis
## Self-Checkout Supermarket — Monolithic Implementation

---

## Architectural Characteristics

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
- Each concern (catalog, transactions, inventory, analytics) must live in its own clearly bounded layer — controller, service, repository — with no cross-cutting dependencies.
- A developer must be able to replace the persistence layer or the HTTP layer without touching business logic.

**Observability**
- Low-stock alerts are persisted to the database and queryable at any time via `/inventory/low-stock`.
- The top-10 popular items from the current hopping window are persisted to the database every 500 scans and queryable via `/analytics/popular-items`.

---

## Top 3 Prioritized Characteristics

### 1. Data Integrity (highest priority)

**Requirement:** No SKU's stock goes negative; the decrement invariant holds under concurrent load.

**Implementation decision:** Every stock decrement uses a single atomic SQL UPDATE with a `stock >= quantity` guard. The database row-level lock ensures two stations cannot both succeed in buying the last unit of an item. SKUs are sorted alphabetically before decrementing so that concurrent complete-transaction calls always acquire row locks in the same order, preventing deadlocks.

**Trade-off:** Row-level locking on the inventory table serialises competing decrements for the same SKU. Under stress load with 100 stations and Zipf-skewed item popularity, the most popular SKUs become a contention hotspot, increasing p99 latency on complete-transaction. This is an honest trade: correctness is not negotiable; latency can be improved in later weeks with optimistic concurrency or event-sourced inventory.

---

### 2. Performance (second priority)

**Requirement:** p99 complete-transaction under 500 ms at default load; p95 under 2 s at stress load.

**Implementation decision:** Spring Boot 3.3 with Java 21 virtual threads (`spring.threads.virtual.enabled=true`) is used so that 100 concurrent blocking DB calls consume no OS threads. The item catalog is cached in memory after the first request so `GET /items` is essentially free on every subsequent call. The sliding window for popular items is maintained as an in-memory deque and only flushed to the database every 500 scans, keeping the hot scan path to a single INSERT per scan rather than a window query.

**Trade-off:** Transaction items are stored in the database (not in memory) to satisfy Data Integrity. This costs one extra DB round-trip per scan compared to a pure in-memory basket. The latency cost is visible in the stress-run numbers and is a deliberate architectural choice.

---

### 3. Modularity (third priority)

**Requirement:** Each concern (catalog, transactions, inventory, analytics) must live in its own clearly bounded layer with no cross-cutting dependencies so the codebase can be adapted to a different architecture style each week.

**Implementation decision:** Controllers delegate to services; services delegate to repositories. No controller touches a repository directly. No service imports another service (except `TransactionService` calling `AnalyticsService.recordScan`, which is a deliberate one-way dependency). The PostgreSQL schema is managed by a plain SQL init script rather than embedded migrations, so the same schema file is shared across any language implementation.

**Trade-off:** The layered structure adds boilerplate — interfaces, DTOs, repository classes — that a single-class monolith would not need. This slightly increases initial code volume for week 1, in exchange for predictability in weeks 2–6 when the architecture changes but the business logic does not.

---

## Load Test Results

### Default run — 10 stations, 60 s

| Operation            |   OK | Errors | Mean ms | p50 ms | p95 ms | p99 ms |
|----------------------|------|--------|---------|--------|--------|--------|
| START_TRANSACTION    | 4494 |      0 |    6.4  |    5.8 |    9.7 |   15.0 |
| SCAN_ITEM            |47010 |      0 |    8.8  |    8.1 |   13.3 |   20.1 |
| COMPLETE_TRANSACTION | 4494 |      0 |   35.2  |   30.4 |   76.1 |  114.4 |

Throughput: **74.7 transactions/sec**, **781.5 items/sec** — 0 % error rate.

### Stress run — 100 stations, 120 s

| Operation            |   OK | Errors | Mean ms | p50 ms |  p95 ms |  p99 ms |
|----------------------|------|--------|---------|--------|---------|---------|
| START_TRANSACTION    | 5688 |      0 |  132.2  |  106.6 |   283.2 |   564.3 |
| SCAN_ITEM            |59865 |      0 |  141.4  |  109.0 |   309.7 |   666.4 |
| COMPLETE_TRANSACTION | 5674 |     14 |  590.1  |  211.1 | 2 633.0 | 5 630.8 |

Throughput: **43.2 transactions/sec** — 0.25 % error rate (14 timeouts on complete-transaction, caused by lock contention on the most popular SKUs under 100-station load — see Data Integrity trade-off above).
