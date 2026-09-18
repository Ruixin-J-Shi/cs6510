use crate::state::AppState;
use chrono::Utc;
use sqlx::Row;
use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::sync::Arc;

/// Record a scanned SKU into the sliding window and trigger a compute every
/// `slide_interval` scans.
pub async fn record_scan(state: Arc<AppState>, sku: String) {
    {
        let mut window = state.scan_window.lock().unwrap();
        window.push_back(sku);
        if window.len() > state.window_size {
            window.pop_front();
        }
    }

    let prev = state.scan_counter.fetch_add(1, Ordering::Relaxed);
    let new_count = prev + 1;

    if new_count % state.slide_interval == 0 {
        let state_clone = Arc::clone(&state);
        tokio::task::spawn(async move {
            compute_and_persist(state_clone).await;
        });
    }
}

/// Compute top-10 SKUs from the in-memory window and upsert to popular_items.
pub async fn compute_and_persist(state: Arc<AppState>) {
    // Tally occurrences inside a short lock window
    let counts: HashMap<String, usize> = {
        let window = state.scan_window.lock().unwrap();
        let mut map: HashMap<String, usize> = HashMap::new();
        for sku in window.iter() {
            *map.entry(sku.clone()).or_insert(0) += 1;
        }
        map
    };

    if counts.is_empty() {
        return;
    }

    // Sort by count desc, sku asc; take top 10
    let mut sorted: Vec<(String, usize)> = counts.into_iter().collect();
    sorted.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
    sorted.truncate(10);

    let now = Utc::now();
    let window_end: i64 = now.timestamp_millis();
    let window_start: i64 = window_end - (state.window_size as i64 * 1000);
    let window_size: i32 = state.window_size as i32;
    let slide_interval: i32 = state.slide_interval as i32;

    // Look up item names from inventory (best-effort; fall back to sku)
    let pool = &state.pool;
    let mut name_map: HashMap<String, String> = HashMap::new();
    for (sku, _) in &sorted {
        let result = sqlx::query("SELECT name FROM inventory WHERE sku = $1")
            .bind(sku)
            .fetch_optional(pool)
            .await;
        if let Ok(Some(row)) = result {
            let name: String = row.get("name");
            name_map.insert(sku.clone(), name);
        }
    }

    // Persist in a transaction: truncate then insert all rows
    let mut tx = match pool.begin().await {
        Ok(t) => t,
        Err(e) => {
            tracing::error!("analytics: begin transaction failed: {}", e);
            return;
        }
    };

    if let Err(e) = sqlx::query("TRUNCATE TABLE popular_items")
        .execute(&mut *tx)
        .await
    {
        tracing::error!("analytics: truncate popular_items failed: {}", e);
        return;
    }

    for (idx, (sku, scan_count)) in sorted.iter().enumerate() {
        let rank: i32 = (idx + 1) as i32;
        let name = name_map.get(sku).cloned().unwrap_or_else(|| sku.clone());
        let scan_count_i32: i32 = *scan_count as i32;

        let res = sqlx::query(
            "INSERT INTO popular_items \
             (rank, sku, name, scan_count, window_size, slide_interval, window_start, window_end, computed_at) \
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
        )
        .bind(rank)
        .bind(sku)
        .bind(&name)
        .bind(scan_count_i32)
        .bind(window_size)
        .bind(slide_interval)
        .bind(window_start)
        .bind(window_end)
        .bind(now)
        .execute(&mut *tx)
        .await;

        if let Err(e) = res {
            tracing::error!("analytics: insert popular_items row failed: {}", e);
            return;
        }
    }

    if let Err(e) = tx.commit().await {
        tracing::error!("analytics: commit failed: {}", e);
    }
}
