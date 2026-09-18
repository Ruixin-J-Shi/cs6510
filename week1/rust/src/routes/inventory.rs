use crate::{
    errors::AppError,
    models::{LowStockAlert, LowStockQuery, LowStockResponse},
    state::AppState,
};
use axum::{
    extract::{Query, State},
    Json,
};
use chrono::Utc;
use sqlx::Row;
use std::sync::Arc;

pub async fn low_stock(
    State(state): State<Arc<AppState>>,
    Query(params): Query<LowStockQuery>,
) -> Result<Json<LowStockResponse>, AppError> {
    let threshold = params.threshold.unwrap_or(state.low_stock_threshold);

    let rows = sqlx::query(
        "SELECT sku, name, stock, low_stock_at \
         FROM inventory \
         WHERE stock < $1 AND low_stock_at IS NOT NULL \
         ORDER BY sku",
    )
    .bind(threshold)
    .fetch_all(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let alerts: Vec<LowStockAlert> = rows
        .iter()
        .map(|row| LowStockAlert {
            sku: row.get("sku"),
            name: row.get("name"),
            current_stock: row.get("stock"),
            threshold,
            triggered_at: row.get("low_stock_at"),
        })
        .collect();

    Ok(Json(LowStockResponse {
        threshold,
        generated_at: Utc::now(),
        alerts,
    }))
}
