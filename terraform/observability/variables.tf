variable "cluster_endpoint" {
  type        = string
  description = "Endpoint del cluster (https://...) pasado desde la raíz"
}

variable "cluster_ca" {
  type        = string
  description = "Cluster CA certificate (base64) pasado desde la raíz"
}

variable "project_id" {
  type        = string
  description = "ID del proyecto GCP (pasado desde la raíz)"
}

variable "region" {
  type        = string
  description = "Región (pasada desde la raíz)"
}