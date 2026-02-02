resource "helm_release" "loki" {
  name       = "loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  namespace  = kubernetes_namespace.observability.metadata[0].name

  values = [
    file("${path.module}/../values/loki-values.yaml")
  ]
}