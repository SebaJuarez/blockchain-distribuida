resource "helm_release" "prometheus" {
  name       = "prometheus-v2"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  create_namespace = false
  wait             = false
  wait_for_jobs    = false 
  timeout          = 900 
  atomic           = false
  cleanup_on_fail  = true
  force_update     = true

  values = [
    <<-EOT
    grafana:
      enabled: false
    defaultRules:
      enabled: false 
    prometheusOperator:
      admissionWebhooks:
        enabled: false
        patch:
          enabled: false
      tls:
        enabled: false
    prometheus:
      prometheusSpec:
        resources:
          requests:
            cpu: "100m"
            memory: "400Mi"
          limits:
            cpu: "500m"
            memory: "800Mi"
        serviceMonitorSelectorNilUsesHelmValues: false
    EOT
  ]
}