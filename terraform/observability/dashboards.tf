resource "kubernetes_config_map_v1" "dashboard_mining_overview" {
  metadata {
    name      = "grafana-dashboard-mining-overview"
    namespace = kubernetes_namespace_v1.observability.metadata[0].name
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "mining-overview.json" = file("${path.module}/dashboard-mining-overview.json")
  }

  depends_on = [kubernetes_namespace_v1.observability]
}

resource "kubernetes_config_map_v1" "dashboard_logs_apps" {
  metadata {
    name      = "grafana-dashboard-logs-apps"
    namespace = kubernetes_namespace_v1.observability.metadata[0].name
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "logs-apps.json" = file("${path.module}/dashboard-logs-apps.json")
  }

  depends_on = [kubernetes_namespace_v1.observability]
}
