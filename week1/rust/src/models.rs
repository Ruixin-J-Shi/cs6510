use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

// ---- Catalog ----

#[derive(Serialize)]
pub struct Item {
    pub sku: String,
    pub name: String,
    pub price: f64,
}

#[derive(Serialize)]
pub struct ItemsResponse {
    pub items: Vec<Item>,
}

// ---- Transactions ----

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateTransactionRequest {
    pub station_id: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TransactionResponse {
    pub transaction_id: String,
    pub station_id: String,
    pub status: String,
    pub item_count: i32,
    pub running_total: f64,
    pub started_at: DateTime<Utc>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TransactionDetailResponse {
    pub transaction_id: String,
    pub station_id: String,
    pub status: String,
    pub item_count: i32,
    pub running_total: f64,
    pub started_at: DateTime<Utc>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub completed_at: Option<DateTime<Utc>>,
}

#[derive(Deserialize)]
pub struct AddItemRequest {
    pub sku: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AddItemResponse {
    pub transaction_id: String,
    pub sku: String,
    pub name: String,
    pub unit_price: f64,
    pub item_count: i32,
    pub running_total: f64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceiptLine {
    pub sku: String,
    pub name: String,
    pub quantity: i64,
    pub unit_price: f64,
    pub line_total: f64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceiptResponse {
    pub transaction_id: String,
    pub station_id: String,
    pub status: String,
    pub item_count: i32,
    pub running_total: f64,
    pub started_at: DateTime<Utc>,
    pub completed_at: DateTime<Utc>,
    pub lines: Vec<ReceiptLine>,
}

// ---- Inventory ----

#[derive(Deserialize)]
pub struct LowStockQuery {
    pub threshold: Option<i32>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LowStockAlert {
    pub sku: String,
    pub name: String,
    pub current_stock: i32,
    pub threshold: i32,
    pub triggered_at: DateTime<Utc>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LowStockResponse {
    pub threshold: i32,
    pub generated_at: DateTime<Utc>,
    pub alerts: Vec<LowStockAlert>,
}

// ---- Analytics ----

#[derive(Deserialize)]
pub struct PopularItemsQuery {
    pub limit: Option<i64>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PopularItem {
    pub sku: String,
    pub name: String,
    pub scan_count: i32,
    pub rank: i32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PopularItemsResponse {
    pub window_size: i32,
    pub slide_interval: i32,
    pub window_start: i64,
    pub window_end: i64,
    pub computed_at: DateTime<Utc>,
    pub items: Vec<PopularItem>,
}

// ---- Admin ----

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResetResponse {
    pub message: String,
    pub items_reset: i64,
}
