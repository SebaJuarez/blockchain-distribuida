resource "null_resource" "wait_for_prometheus_operator" {
  provisioner "local-exec" {
    command = <<EOT
      kubectl wait --for=condition=available --timeout=600s deployment/kube-prometheus-kube-prome-operator -n observability
    EOT
  }

  depends_on = [ helm_release.prometheus ]
}