resource "helm_release" "prometheus" {
  name       = "kube-prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  create_namespace = false
  
  wait          = true
  wait_for_jobs = true
  timeout       = 900 
  atomic        = false

  values = [
    <<-EOT
    grafana:
      enabled: false

    prometheusOperator:
      admissionWebhooks:
        enabled: false
        patch:
          enabled: false # Esto quita los errores de "failed calling webhook"
    
    prometheus:
      prometheusSpec:
        serviceMonitorSelectorNilUsesHelmValues: false
    EOT
  ]
}