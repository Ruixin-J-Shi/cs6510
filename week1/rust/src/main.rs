mod analytics;
mod errors;
mod models;
mod routes;
mod state;

use axum::Router;
use sqlx::postgres::PgPoolOptions;
use state::AppState;
use std::sync::Arc;
use tower_http::cors::CorsLayer;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() {
    // Initialise tracing; respect RUST_LOG env var, default to "info"
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .init();

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| {
            "postgres://checkout:checkout@localhost:5433/checkout".to_string()
        });

    tracing::info!("Connecting to database…");
    let pool = PgPoolOptions::new()
        .max_connections(10)
        .connect(&database_url)
        .await
        .expect("Failed to connect to Postgres");

    let app_state = Arc::new(AppState::new(pool));

    let cors = CorsLayer::permissive();

    let app = Router::new()
        // Catalog
        .route("/items", axum::routing::get(routes::catalog::list_items))
        // Transactions
        .route(
            "/transactions",
            axum::routing::post(routes::transactions::create_transaction),
        )
        .route(
            "/transactions/:id",
            axum::routing::get(routes::transactions::get_transaction),
        )
        .route(
            "/transactions/:id/items",
            axum::routing::post(routes::transactions::add_item),
        )
        .route(
            "/transactions/:id/complete",
            axum::routing::post(routes::transactions::complete_transaction),
        )
        // Inventory
        .route(
            "/inventory/low-stock",
            axum::routing::get(routes::inventory::low_stock),
        )
        // Analytics
        .route(
            "/analytics/popular-items",
            axum::routing::get(routes::analytics::popular_items),
        )
        // Admin
        .route("/admin/reset", axum::routing::post(routes::admin::reset))
        // Middleware
        .layer(cors)
        .with_state(app_state);

    let addr = "0.0.0.0:8081";
    tracing::info!("Listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind port 8081");

    axum::serve(listener, app)
        .await
        .expect("Server error");
}
