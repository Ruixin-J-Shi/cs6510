use crate::{
    errors::AppError,
    models::{Item, ItemsResponse},
    state::AppState,
};
use axum::{extract::State, Json};
use sqlx::Row;
use std::sync::Arc;

pub async fn list_items(
    State(state): State<Arc<AppState>>,
) -> Result<Json<ItemsResponse>, AppError> {
    let rows = sqlx::query(
        "SELECT sku, name, CAST(price AS FLOAT8) AS price \
         FROM inventory \
         ORDER BY sku",
    )
    .fetch_all(&state.pool)
    .await
    .map_err(|e| AppError::Internal(e.to_string()))?;

    let items: Vec<Item> = rows
        .iter()
        .map(|row| Item {
            sku: row.get("sku"),
            name: row.get("name"),
            price: row.get("price"),
        })
        .collect();

    Ok(Json(ItemsResponse { items }))
}
