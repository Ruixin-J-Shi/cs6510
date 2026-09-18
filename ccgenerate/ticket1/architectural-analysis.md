# CS6510 Week 1 — Architectural Characteristics Analysis
## Self-Checkout Supermarket Monolith

---

## Architectural Characteristics

**Performance**
- Each of the three primary operations (start transaction, scan item, complete transaction) must complete in under 500 ms at p99 under default load (10 stations, 60 s).
- Under stress load (100 stations, 120 s) p95 must remain under 2 s.
- The full item catalog (2 000 SKUs) must be returned in under 200 ms.

**Data Integrity**
- Stock must never go negative for any SKU.
- For every SKU, the invariant `initial_stock − final_stock == total completed-transaction line items for that SKU` must hold after every test run, including concurrent stress runs.
- Enforced via atomic SQL: `UPDATE inventory SET stock = stock - 1 WHERE sku = ? AND stock >= 1`.

**Scalability**
- The system must handle at least 100 concurrent checkout stations without an error rate exceeding 1%.
- Throughput must scale with the number of stations up to the hardware limit of the host machine.

**Reliability**
- In-flight transaction items survive a failed or retried complete attempt; no phantom stock decrements occur on a retried complete.
- Completed transactions are persisted and cannot be re-completed (409 on duplicate complete).

**Modularity**
- Code is structured in distinct layers (HTTP controllers, service layer, repository/DAO layer) so individual layers can be extracted into separate services in later weeks without rewriting business logic.
- The database schema is owned by an init script, not embedded in the application, making it portable across implementations (Java, Rust mirror).

**Observability**
- Low-stock alerts are persisted and queryable at any time via `/inventory/low-stock`.
- The top-10 popular items from the current hopping window are persisted to the database every 500 scans and queryable via `/analytics/popular-items`.

---

## Top 3 Prioritized Characteristics

### 1. Data Integrity (highest priority)

**Requirement:** No SKU's stock goes negative; the decrement invariant holds under concurrent load.

**Implementation decision:** Every stock decrement uses a single atomic SQL UPDATE with a `stock >= 1` guard. The database row-level lock ensures two stations cannot both succeed in buying the last unit of an item.

**Trade-off:** Row-level locking on the inventory table serializes competing decrements for the same SKU. Under stress load (100 stations with Zipf-skewed item popularity), the most popular SKUs become a contention hotspot, increasing p99 latency for complete-transaction. This is an honest trade: correctness is not negotiable; latency can be improved in later weeks with optimistic locking or event-sourced inventory.

---

### 2. Performance (second priority)

**Requirement:** p99 complete-transaction under 500 ms at default load; p95 under 2 s at stress load.

**Implementation decision:** Transaction items are stored in the database (not in memory) to satisfy Data Integrity. The sliding window for popular items is maintained as an in-memory circular buffer and only flushed to the database every 500 scans, keeping the hot path (scan item) to a single INSERT rather than a window query on every call.

**Trade-off:** Storing transaction items in the database costs one extra DB round-trip per scan compared to a pure in-memory cache. This hurts raw scan throughput but is necessary to guarantee no phantom decrements if a station's connection drops mid-transaction. The latency cost is measurable in the load-client report and is part of the architecture's honest data.

---

### 3. Modularity (third priority)

**Requirement:** Each concern (catalog, transactions, inventory, analytics) must live in its own clearly bounded layer (controller, service, repository) with no cross-cutting dependencies. A developer must be able to replace the persistence layer or the HTTP layer without touching business logic.

**Implementation decision:** Controllers, services, and repositories are strict layers with no cross-cutting. The PostgreSQL schema is seeded via a standalone SQL script (not Flyway or embedded Spring Boot migrations) so the Rust mirror implementation and future service extractions can share the same schema file.

**Trade-off:** The layered structure adds boilerplate (interfaces, DTOs, repository classes) that a single-class monolith would not need. This slightly increases initial development time and code volume for week 1, in exchange for reduced friction in weeks 2–6 when the architecture changes but the business logic does not.
