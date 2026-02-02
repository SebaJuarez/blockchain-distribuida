module "observability" {
  source = "./observability"

  cluster_endpoint = "https://${google_container_cluster.primary.endpoint}"
  cluster_ca       = google_container_cluster.primary.master_auth[0].cluster_ca_certificate

}

resource "null_resource" "wait_observability_dep" {
  depends_on = [ google_container_cluster.primary, module.observability ]
}
