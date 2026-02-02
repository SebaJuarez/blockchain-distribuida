resource "helm_release" "grafana" {
  name       = "grafana"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name
  values = [ file("${path.module}/../values/grafana-values.yaml") ]

  timeout = 600
  wait    = true

  depends_on = [
    helm_release.prometheus,
    helm_release.loki
  ]
}

resource "kubernetes_service_v1" "grafana_lb" {
  metadata {
    name      = "grafana-lb"
    namespace  = kubernetes_namespace_v1.observability.metadata[0].name
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