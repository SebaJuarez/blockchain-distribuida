resource "helm_release" "prometheus_adapter" {
  name       = "prometheus-adapter"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-adapter"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name
  depends_on = [helm_release.prometheus]

  values = [<<-EOT
    prometheus:
      url: http://prometheus-v2-kube-prometheus-prometheus.observability.svc
      port: 9090
    rules:
      default: false
      external:
        - seriesQuery: 'mining_transactions_pending'
          resources: { overrides: { namespace: { resource: "namespace" } } }
          name: { as: "mining_transactions_pending" }
          metricsQuery: 'avg(mining_transactions_pending)'
  EOT
  ]
}