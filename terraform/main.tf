terraform {
  required_version = ">= 0.13"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 5.0, < 7.0" 
    }
  }
  backend "gcs" {
    bucket = var.bucket_backend
    prefix = var.backend_prefix
  }
}

# --- VARIABLES ---

variable "bucket_backend" {
  type        = string
  description = "GCS bucket para el estado de Terraform"
}

variable "backend_prefix" {
  type        = string
  description = "Path/prefix dentro del bucket"
}

variable "project_id" {
  description = "ID del proyecto de GCP"
  type        = string
}

variable "region" {
  description = "Región de GCP"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona de GCP para clúster zonal"
  type        = string
  default     = "us-central1-a"
}

variable "credentials_file" {
  description = "Ruta al archivo de credenciales (terraform-sa-key.json)"
  type        = string
}

variable "cluster_name" {
  description = "Nombre del clúster GKE"
  type        = string
  default     = "gke-cluster"
}

variable "machine_type" {
  description = "Tipo de máquina para los nodos"
  type        = string
  default     = "e2-medium"
}

variable "node_count" {
  description = "Número de nodos mínimo para pools estáticos"
  type        = number
  default     = 1
}

variable "boot_disk_size_gb" {
  description = "Tamaño del disco de arranque en GB"
  type        = number
  default     = 20
}

variable "disk_type" {
  description = "Tipo de disco de arranque"
  type        = string
  default     = "pd-standard"
}

variable "worker_min_nodes" {
  description = "Mínimo de nodos para el pool dinámico de workers"
  type        = number
  default     = 0
}

variable "worker_max_nodes" {
  description = "Máximo de nodos para el pool dinámico de workers"
  type        = number
  default     = 5
}

variable "network" {
  description = "VPC donde desplegar clúster y VMs"
  type        = string
  default     = "default"
}

variable "subnetwork" {
  description = "Subred dentro de la VPC"
  type        = string
  default     = "default"
}

variable "infra_node_tag" {
  description = "Etiqueta aplicada a nodos infra en GKE"
  type        = string
  default     = "gke-infra-pool"
}

# Provider
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# --- IAM Y CUENTAS DE SERVICIO ---

# Cuenta para Terraform (terraform-sa)

resource "google_project_iam_member" "terraform_sa_roles" {
  for_each = toset([
    "roles/storage.admin",
    "roles/storage.objectAdmin",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.workloadIdentityPoolAdmin",
    "roles/resourcemanager.projectIamAdmin",
    "roles/compute.serviceAgent",
    "roles/container.serviceAgent",
    "roles/iam.serviceAccountTokenCreator",
    "roles/editor",
    "roles/viewer"
  ])
  project = var.project_id
  role    = each.key
  member  = "serviceAccount:terraform-sa@${var.project_id}.iam.gserviceaccount.com"
}

# Cuenta para los Miners (python-miner-sa)
resource "google_service_account" "python_miner" {
  project    = var.project_id
  account_id = "python-miner-sa"
}

resource "google_project_iam_member" "python_miner_roles" {
  for_each = toset([
    "roles/monitoring.metricWriter",
    "roles/compute.instanceAdmin.v1",
    "roles/iam.serviceAccountTokenCreator"
  ])
  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.python_miner.email}"
}

# Workload Identity (Permisos para que K8s use la cuenta de Google)
resource "google_service_account_iam_member" "allow_k8s_infra_impersonate" {
  service_account_id = google_service_account.python_miner.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[infra/blockchain-sa]"
}

resource "google_service_account_iam_member" "allow_k8s_apps_impersonate" {
  service_account_id = google_service_account.python_miner.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[apps/blockchain-sa]"
}

# --- INFRAESTRUCTURA DE RED ---

resource "google_compute_network" "vpc" {
  name                    = var.network
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "subnet" {
  name          = var.subnetwork
  ip_cidr_range = "10.0.0.0/16"
  region        = var.region
  network       = google_compute_network.vpc.id

  secondary_ip_range {
    range_name    = "gke-cluster-secondary-range"
    ip_cidr_range = "10.1.0.0/20"
  }

  secondary_ip_range {
    range_name    = "gke-services-secondary-range"
    ip_cidr_range = "10.2.0.0/20"
  }
}

# --- GKE CLUSTER ---

resource "google_container_cluster" "primary" {
  name                     = var.cluster_name
  location                 = var.zone
  remove_default_node_pool = true
  networking_mode          = "VPC_NATIVE"
  network                  = google_compute_network.vpc.name
  subnetwork               = google_compute_subnetwork.subnet.name
  initial_node_count       = 1
  deletion_protection      = false

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  ip_allocation_policy {
    cluster_secondary_range_name  = "gke-cluster-secondary-range"
    services_secondary_range_name = "gke-services-secondary-range"
  }

  node_config {
    machine_type = var.machine_type
    disk_size_gb = var.boot_disk_size_gb
    disk_type    = var.disk_type
    oauth_scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }
}

resource "google_container_node_pool" "infra" {
  name     = "${var.cluster_name}-infra"
  cluster  = google_container_cluster.primary.name
  location = var.zone
  node_config {
    machine_type = var.machine_type
    disk_size_gb = var.boot_disk_size_gb
    disk_type    = var.disk_type
    labels       = { role = "infra" }
    tags         = [var.infra_node_tag]
  }
  autoscaling {
    min_node_count = var.node_count
    max_node_count = var.node_count * 2
  }
}

resource "google_container_node_pool" "apps" {
  name     = "${var.cluster_name}-apps"
  cluster  = google_container_cluster.primary.name
  location = var.zone
  node_config {
    machine_type = var.machine_type
    disk_size_gb = var.boot_disk_size_gb
    disk_type    = var.disk_type
    labels       = { role = "app" }
    oauth_scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }
  autoscaling {
    min_node_count = var.node_count
    max_node_count = var.node_count * 2
  }
}

resource "google_compute_address" "rabbitmq_internal_ip" {
  name         = "rabbitmq-internal-ip"
  region       = var.region
  subnetwork   = google_compute_subnetwork.subnet.id
  address_type = "INTERNAL"
}

# --- COMPUTE WORKERS (MIG) ---

resource "google_compute_instance_template" "python_miner" {
  name_prefix = "python-miner-"
  tags        = ["python-miner"]

  service_account {
    email  = google_service_account.python_miner.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  disk {
    boot         = true
    auto_delete  = true
    source_image = "projects/debian-cloud/global/images/family/debian-12"
    disk_size_gb = var.boot_disk_size_gb
    disk_type    = var.disk_type
  }

  machine_type = var.machine_type
  metadata_startup_script = file("${path.module}/startup-miner.sh")

  network_interface {
    network    = google_compute_network.vpc.id
    subnetwork = google_compute_subnetwork.subnet.id
    access_config {}
  }
}

resource "google_compute_instance_group_manager" "python_miners" {
  name               = "python-miners-mig"
  zone               = var.zone
  version {
    instance_template = google_compute_instance_template.python_miner.self_link
  }
  base_instance_name = "python-miner"
  target_size        = var.worker_min_nodes
}

# --- FIREWALL ---

resource "google_compute_firewall" "allow-ssh" {
  name    = "allow-ssh"
  network = google_compute_network.vpc.name
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow-http" {
  name    = "allow-http"
  network = google_compute_network.vpc.name
  allow {
    protocol = "tcp"
    ports    = ["80"]
  }
  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow-https" {
  name    = "allow-https"
  network = google_compute_network.vpc.name
  allow {
    protocol = "tcp"
    ports    = ["443"]
  }
  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow-rabbitmq1" {
  name    = "allow-rabbitmq1"
  network = google_compute_network.vpc.name
  allow {
    protocol = "tcp"
    ports    = ["5672", "15672"]
  }
  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow-rabbit-from-miners-to-ilb" {
  name    = "allow-rabbit-from-miners-to-ilb"
  network = google_compute_network.vpc.name

  allow {
    protocol = "tcp"
    ports    = ["5672", "15672"]
  }

  source_tags        = ["python-miner"] 
  destination_ranges = [google_compute_subnetwork.subnet.ip_cidr_range]
}

resource "google_compute_firewall" "allow-redis" {
  name    = "allow-redis"
  network = google_compute_network.vpc.name
  allow {
    protocol = "tcp"
    ports    = ["6379", "8001"]
  }
  source_ranges = ["0.0.0.0/0"]
}

# --- OTROS RECURSOS ---

resource "tls_private_key" "ssh_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "local_file" "ssh_private_key_pem" {
  content         = tls_private_key.ssh_key.private_key_pem
  filename        = ".ssh/google_compute_engine"
  file_permission = "0600"
}

resource "google_dns_managed_zone" "internal" {
  name        = "internal-zone"
  dns_name    = "internal."
  visibility  = "private"
  private_visibility_config {
    networks {
      network_url = google_compute_network.vpc.self_link
    }
  }
}

resource "google_dns_record_set" "rabbitmq" {
  name         = "rabbitmq.internal."
  managed_zone = google_dns_managed_zone.internal.name
  type         = "A"
  ttl          = 300
  rrdatas      = [google_compute_address.rabbitmq_internal_ip.address]

  depends_on = [ google_compute_address.rabbitmq_internal_ip ]
}
