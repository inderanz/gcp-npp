resource "google_spanner_instance" "spanner_instance" {
  name        = "my-spanner-instance"
  display_name = "My Spanner Instance"
  config       = "regional-us-central1"
  node_count   = 1
  labels = {
    environment = "production"
  }
}
