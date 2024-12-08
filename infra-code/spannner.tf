# spannner.tf

# Provider Configuration
provider "google" {
  project = "spanner-gke-443910"  # Replace with your actual GCP project ID
  region  = "us-central1"
}

# Spanner Database Configuration
resource "google_spanner_database" "sample_game" {
  name     = "sample-game"
  instance = "projects/spanner-gke-443910/instances/sample-instance"  # Full instance path

  ddl = [
    "CREATE TABLE Transactions (TransactionId STRING(36) NOT NULL, UserId STRING(36) NOT NULL, Amount FLOAT64 NOT NULL, Timestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)) PRIMARY KEY (TransactionId);",
    "CREATE TABLE PaymentState (PUID STRING(36) NOT NULL, History STRING(MAX) NOT NULL, Timestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)) PRIMARY KEY (PUID);",
    "CREATE TABLE Payments (PaymentUID STRING(36) NOT NULL, UserId STRING(36) NOT NULL, Amount FLOAT64 NOT NULL, Status STRING(20) NOT NULL, Timestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)) PRIMARY KEY (PaymentUID);"
  ]

  lifecycle {
    prevent_destroy = false  # Prevent accidental deletion
  }
}

# Output the Spanner Database URI
output "spanner_database_uri" {
  value = google_spanner_database.sample_game.id
}
