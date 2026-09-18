-- ============================================================
-- Schema for CS6510 Week 1 — Monolithic Self-Checkout Backend
-- ============================================================

CREATE TABLE IF NOT EXISTS inventory (
    sku            VARCHAR(20)    PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    price          NUMERIC(10,2)  NOT NULL,
    stock          INTEGER        NOT NULL DEFAULT 0,
    low_stock_at   TIMESTAMPTZ    -- set once when stock first drops below threshold
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(50)   PRIMARY KEY,
    station_id     VARCHAR(50)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'OPEN',   -- OPEN | COMPLETED | CANCELLED
    item_count     INTEGER       NOT NULL DEFAULT 0,
    running_total  NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    started_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS transaction_items (
    id             BIGSERIAL     PRIMARY KEY,
    transaction_id VARCHAR(50)   NOT NULL REFERENCES transactions(transaction_id),
    sku            VARCHAR(20)   NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    unit_price     NUMERIC(10,2) NOT NULL,
    scanned_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tx_items_tx_id ON transaction_items(transaction_id);

-- Stores the latest sliding-window top-10 snapshot.
-- rank 1-10, fully replaced on each slide.
CREATE TABLE IF NOT EXISTS popular_items (
    rank           INTEGER       PRIMARY KEY,
    sku            VARCHAR(20)   NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    scan_count     INTEGER       NOT NULL,
    window_size    INTEGER       NOT NULL,
    slide_interval INTEGER       NOT NULL,
    window_start   BIGINT        NOT NULL,
    window_end     BIGINT        NOT NULL,
    computed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
