resource "kubernetes_namespace_v1" "observability" {
  metadata {
    name = "observability"
  }
}