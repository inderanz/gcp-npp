resource "google_container_cluster" "gke_spanner_cluster" {
  name     = "game-spanner-gke"
  location = "us-central1"
}
