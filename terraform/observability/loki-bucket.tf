# terraform/observability/loki_bucket.tf
resource "google_storage_bucket" "loki_bucket" {
  name          = "loki-logs-${var.project_id}"
  location      = var.region
  force_destroy = true
}