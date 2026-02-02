resource "helm_release" "alloy" {
  name       = "alloy"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "alloy"
  namespace  = kubernetes_namespace.observability.metadata[0].name

  values = [
    file("${path.module}/../values/alloy-values.yaml")
  ]
}