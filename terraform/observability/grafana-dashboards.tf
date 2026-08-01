resource "kubernetes_config_map_v1" "grafana_dashboard_mining" {
  metadata {
    name      = "grafana-dashboard-mining-overview"
    namespace = "observability"
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "mining-overview.json" = file("${path.module}/../values/dashboards/mining-overview.json")
  }
}