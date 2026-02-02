resource "helm_release" "grafana" {
  name       = "grafana"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  namespace  = kubernetes_namespace.observability.metadata[0].name

  values = [
    file("${path.module}/../values/grafana-values.yaml")
  ]
}

# Exponer Grafana hacia afuera del cluster
resource "kubernetes_service" "grafana_lb" {
  metadata {
    name      = "grafana-lb"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }

  spec {
    type = "LoadBalancer"
    selector = {
      "app.kubernetes.io/name" = "grafana"
    }
    port {
      port        = 80
      target_port = 3000
    }
  }

  depends_on = [helm_release.grafana]
}