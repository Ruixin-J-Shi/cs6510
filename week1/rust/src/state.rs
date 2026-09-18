use sqlx::PgPool;
use std::collections::VecDeque;
use std::sync::atomic::AtomicU64;
use std::sync::{Arc, Mutex};

pub struct AppState {
    pub pool: PgPool,
    pub scan_window: Arc<Mutex<VecDeque<String>>>,
    pub scan_counter: Arc<AtomicU64>,
    pub window_size: usize,
    pub slide_interval: u64,
    pub low_stock_threshold: i32,
}

impl AppState {
    pub fn new(pool: PgPool) -> Self {
        Self {
            pool,
            scan_window: Arc::new(Mutex::new(VecDeque::new())),
            scan_counter: Arc::new(AtomicU64::new(0)),
            window_size: 1000,
            slide_interval: 500,
            low_stock_threshold: 50,
        }
    }
}
