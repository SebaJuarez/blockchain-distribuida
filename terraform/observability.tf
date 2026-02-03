module "observability" {
  source = "./observability"

  cluster_endpoint = "https://${google_container_cluster.primary.endpoint}"
  cluster_ca       = google_container_cluster.primary.master_auth[0].cluster_ca_certificate
  project_id = var.project_id
  region     = var.region
}
