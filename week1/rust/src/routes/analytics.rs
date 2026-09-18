use crate::{
    errors::AppError,
    models::{PopularItem, PopularItemsQuery, PopularItemsResponse},
    state::AppState,
};
use axum::{
    extract::{Query, State},
    Json,
};
use chrono::Utc;
use sqlx::Row;
use std::sync::Arc;

pub async fn popular_items(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PopularItemsQuery>,
) -> Result<Json<PopularItemsResponse>, AppError> {
    let limit = params.limit.unwrap_or(10);

    let rows = sqlx::query(
        "SELECT rank, sku, name, scan_count, window_size, slide_interval, \
         window_start, window_end, computed_at \
         FROM popular_items \
         ORDER BY rank \
         LIMIT $1",
    )
    .bind(limit)
    .fetch_all(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    if rows.is_empty() {
        return Ok(Json(PopularItemsResponse {
            window_size: state.window_size as i32,
            slide_interval: state.slide_interval as i32,
            window_start: 0,
            window_end: 0,
            computed_at: Utc::now(),
            items: vec![],
        }));
    }

    // Read metadata from the first row (same for all rows in a batch)
    let first = &rows[0];
    let window_size: i32 = first.get("window_size");
    let slide_interval: i32 = first.get("slide_interval");
    let window_start: i64 = first.get("window_start");
    let window_end: i64 = first.get("window_end");
    let computed_at = first.get("computed_at");

    let items: Vec<PopularItem> = rows
        .iter()
        .map(|row| PopularItem {
            sku: row.get("sku"),
            name: row.get("name"),
            scan_count: row.get("scan_count"),
            rank: row.get("rank"),
        })
        .collect();

    Ok(Json(PopularItemsResponse {
        window_size,
        slide_interval,
        window_start,
        window_end,
        computed_at,
        items,
    }))
}
