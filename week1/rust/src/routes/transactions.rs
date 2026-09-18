use crate::{
    analytics,
    errors::AppError,
    models::{
        AddItemRequest, AddItemResponse, CreateTransactionRequest, ReceiptLine, ReceiptResponse,
        TransactionDetailResponse, TransactionResponse,
    },
    state::AppState,
};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    Json,
};
use chrono::{DateTime, Utc};
use sqlx::Row;
use std::sync::Arc;
use uuid::Uuid;

// ---------------------------------------------------------------------------
// POST /transactions
// ---------------------------------------------------------------------------

pub async fn create_transaction(
    State(state): State<Arc<AppState>>,
    Json(req): Json<CreateTransactionRequest>,
) -> Result<(StatusCode, Json<TransactionResponse>), AppError> {
    let transaction_id = Uuid::new_v4().to_string();

    sqlx::query(
        "INSERT INTO transactions \
         (transaction_id, station_id, status, item_count, running_total, started_at) \
         VALUES ($1, $2, 'OPEN', 0, 0, NOW())",
    )
    .bind(&transaction_id)
    .bind(&req.station_id)
    .execute(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let row = sqlx::query(
        "SELECT transaction_id, station_id, status, item_count, \
         CAST(running_total AS FLOAT8) AS running_total, started_at \
         FROM transactions WHERE transaction_id = $1",
    )
    .bind(&transaction_id)
    .fetch_one(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let resp = TransactionResponse {
        transaction_id: row.get("transaction_id"),
        station_id: row.get("station_id"),
        status: row.get("status"),
        item_count: row.get("item_count"),
        running_total: row.get("running_total"),
        started_at: row.get("started_at"),
    };

    Ok((StatusCode::CREATED, Json(resp)))
}

// ---------------------------------------------------------------------------
// GET /transactions/:id
// ---------------------------------------------------------------------------

pub async fn get_transaction(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<TransactionDetailResponse>, AppError> {
    let row = sqlx::query(
        "SELECT transaction_id, station_id, status, item_count, \
         CAST(running_total AS FLOAT8) AS running_total, started_at, completed_at \
         FROM transactions WHERE transaction_id = $1",
    )
    .bind(&id)
    .fetch_optional(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?
    .ok_or_else(|| AppError::NotFound(format!("Transaction {} not found", id)))?;

    let resp = TransactionDetailResponse {
        transaction_id: row.get("transaction_id"),
        station_id: row.get("station_id"),
        status: row.get("status"),
        item_count: row.get("item_count"),
        running_total: row.get("running_total"),
        started_at: row.get("started_at"),
        completed_at: row.get("completed_at"),
    };

    Ok(Json(resp))
}

// ---------------------------------------------------------------------------
// POST /transactions/:id/items
// ---------------------------------------------------------------------------

pub async fn add_item(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Json(req): Json<AddItemRequest>,
) -> Result<Json<AddItemResponse>, AppError> {
    // Verify transaction exists and is OPEN
    let tx_row = sqlx::query(
        "SELECT status FROM transactions WHERE transaction_id = $1",
    )
    .bind(&id)
    .fetch_optional(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?
    .ok_or_else(|| AppError::NotFound(format!("Transaction {} not found", id)))?;

    let status: String = tx_row.get("status");
    if status != "OPEN" {
        return Err(AppError::Conflict(format!(
            "Transaction {} is not open (status: {})",
            id, status
        )));
    }

    // Look up item in inventory
    let item_row = sqlx::query(
        "SELECT sku, name, CAST(price AS FLOAT8) AS price \
         FROM inventory WHERE sku = $1",
    )
    .bind(&req.sku)
    .fetch_optional(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?
    .ok_or_else(|| AppError::NotFound(format!("Item {} not found", req.sku)))?;

    let sku: String = item_row.get("sku");
    let name: String = item_row.get("name");
    let unit_price: f64 = item_row.get("price");

    // Insert scan record
    sqlx::query(
        "INSERT INTO transaction_items \
         (transaction_id, sku, name, unit_price, scanned_at) \
         VALUES ($1, $2, $3, $4::NUMERIC, NOW())",
    )
    .bind(&id)
    .bind(&sku)
    .bind(&name)
    .bind(unit_price)
    .execute(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    // Atomically update transaction totals
    sqlx::query(
        "UPDATE transactions \
         SET item_count = item_count + 1, \
             running_total = running_total + $2::NUMERIC \
         WHERE transaction_id = $1",
    )
    .bind(&id)
    .bind(unit_price)
    .execute(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    // Re-fetch to get authoritative counts
    let updated = sqlx::query(
        "SELECT item_count, CAST(running_total AS FLOAT8) AS running_total \
         FROM transactions WHERE transaction_id = $1",
    )
    .bind(&id)
    .fetch_one(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let item_count: i32 = updated.get("item_count");
    let running_total: f64 = updated.get("running_total");

    // Record scan in sliding window (fire-and-forget spawn)
    analytics::record_scan(Arc::clone(&state), sku.clone()).await;

    Ok(Json(AddItemResponse {
        transaction_id: id,
        sku,
        name,
        unit_price,
        item_count,
        running_total,
    }))
}

// ---------------------------------------------------------------------------
// POST /transactions/:id/complete
// ---------------------------------------------------------------------------

pub async fn complete_transaction(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<ReceiptResponse>, AppError> {
    // Fetch and validate transaction
    let tx_row = sqlx::query(
        "SELECT transaction_id, station_id, status, item_count, \
         CAST(running_total AS FLOAT8) AS running_total, started_at \
         FROM transactions WHERE transaction_id = $1",
    )
    .bind(&id)
    .fetch_optional(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?
    .ok_or_else(|| AppError::NotFound(format!("Transaction {} not found", id)))?;

    let status: String = tx_row.get("status");
    if status != "OPEN" {
        return Err(AppError::Conflict(format!(
            "Transaction {} is already {}",
            id, status
        )));
    }

    let transaction_id: String = tx_row.get("transaction_id");
    let station_id: String = tx_row.get("station_id");
    let item_count: i32 = tx_row.get("item_count");
    let running_total: f64 = tx_row.get("running_total");
    let started_at: DateTime<Utc> = tx_row.get("started_at");

    // Group items by SKU (sort by SKU for consistent locking order to prevent deadlocks)
    let item_rows = sqlx::query(
        "SELECT sku, name, COUNT(*) AS quantity, \
         CAST(unit_price AS FLOAT8) AS unit_price \
         FROM transaction_items \
         WHERE transaction_id = $1 \
         GROUP BY sku, name, unit_price \
         ORDER BY sku",
    )
    .bind(&id)
    .fetch_all(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    // Decrement stock per SKU (sorted order prevents deadlocks)
    let threshold = state.low_stock_threshold;
    for row in &item_rows {
        let sku: String = row.get("sku");
        let quantity: i64 = row.get("quantity");

        sqlx::query(
            "UPDATE inventory \
             SET stock = stock - $2, \
                 low_stock_at = CASE \
                     WHEN (stock - $2) < $3 AND low_stock_at IS NULL THEN NOW() \
                     ELSE low_stock_at \
                 END \
             WHERE sku = $1 AND stock >= $2",
        )
        .bind(&sku)
        .bind(quantity)
        .bind(threshold as i64)
        .execute(&state.pool)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    }

    // Mark transaction complete
    sqlx::query(
        "UPDATE transactions \
         SET status = 'COMPLETE', completed_at = NOW() \
         WHERE transaction_id = $1",
    )
    .bind(&id)
    .execute(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    // Fetch completed_at
    let completed_row = sqlx::query(
        "SELECT completed_at FROM transactions WHERE transaction_id = $1",
    )
    .bind(&id)
    .fetch_one(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let completed_at: DateTime<Utc> = completed_row.get("completed_at");

    // Build receipt lines
    let lines: Vec<ReceiptLine> = item_rows
        .iter()
        .map(|row| {
            let sku: String = row.get("sku");
            let name: String = row.get("name");
            let quantity: i64 = row.get("quantity");
            let unit_price: f64 = row.get("unit_price");
            ReceiptLine {
                sku,
                name,
                quantity,
                unit_price,
                line_total: (quantity as f64) * unit_price,
            }
        })
        .collect();

    Ok(Json(ReceiptResponse {
        transaction_id,
        station_id,
        status: "COMPLETE".to_string(),
        item_count,
        running_total,
        started_at,
        completed_at,
        lines,
    }))
}
