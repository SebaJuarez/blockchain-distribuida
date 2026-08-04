resource "helm_release" "prometheus_adapter" {
  name       = "prometheus-adapter"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-adapter"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name
  depends_on = [helm_release.prometheus]

  values = [<<-EOT
    prometheus:
      url: http://prometheus-v2-kube-prometh-prometheus.observability.svc
      port: 9090
    nodeSelector:
      role: obs
    tolerations:
      - key: workload
        operator: Equal
        value: obs
        effect: NoSchedule
    rules:
      default: false
      custom:
        - seriesQuery: 'http_server_requests_seconds_count'
          resources: { overrides: { namespace: { resource: "namespace" }, pod: { resource: "pod" } } }
          name: { as: "http_requests_per_second" }
          metricsQuery: 'sum(rate(http_server_requests_seconds_count{<<.LabelMatchers>>}[2m])) by (<<.GroupBy>>)'
  EOT
  ]
}