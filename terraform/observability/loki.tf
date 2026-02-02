resource "helm_release" "loki" {
  name       = "loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  values = [
    templatefile("${path.module}/../values/loki-values.yaml", {
      bucket_name = google_storage_bucket.loki_bucket.name
    })
  ]

  timeout = 1200
  wait    = true
}