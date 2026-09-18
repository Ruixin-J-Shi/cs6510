use crate::{
    errors::AppError,
    models::ResetResponse,
    state::AppState,
};
use axum::{extract::State, Json};
use std::sync::{atomic::Ordering, Arc};

pub async fn reset(
    State(state): State<Arc<AppState>>,
) -> Result<Json<ResetResponse>, AppError> {
    // Truncate transactional tables (transaction_items first due to FK)
    sqlx::query("TRUNCATE TABLE transaction_items, transactions, popular_items")
        .execute(&state.pool)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;

    // Reset all inventory stock and clear low_stock_at
    let result = sqlx::query(
        "UPDATE inventory SET stock = 10000, low_stock_at = NULL",
    )
    .execute(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let items_reset = result.rows_affected() as i64;

    // Reset in-memory sliding window and scan counter
    {
        let mut window = state.scan_window.lock().unwrap();
        window.clear();
    }
    state.scan_counter.store(0, Ordering::SeqCst);

    Ok(Json(ResetResponse {
        message: "Reset complete".to_string(),
        items_reset,
    }))
}
