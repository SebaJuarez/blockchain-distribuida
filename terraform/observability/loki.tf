resource "helm_release" "loki" {
  name       = "loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  set {
    name  = "loki.storage.type"
    value = "gcs"
  }

  set {
    name  = "loki.storage.gcs.bucket_name"
    value = google_storage_bucket.loki_bucket.name
  }

  set {
    name  = "loki.storage.bucketNames.chunks"
    value = google_storage_bucket.loki_bucket.name
  }

  set {
    name  = "singleBinary.replicas"
    value = "1"
  }

  set {
    name  = "singleBinary.persistence.enabled"
    value = "true"
  }
  set {
    name  = "singleBinary.persistence.size"
    value = "5Gi"
  }

  timeout = 1200
  wait    = true

  depends_on = [ helm_release.prometheus ]
}