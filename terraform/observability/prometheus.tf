resource "helm_release" "prometheus" {
  name       = "kube-prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  create_namespace = false

  atomic  = true
  wait    = true
  timeout = 1200

  values = [
    <<-EOT
    grafana:
      enabled: false

    prometheusOperator:
      admissionWebhooks:
        enabled: false
    EOT
  ]
}