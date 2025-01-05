resource "google_spanner_database" "spanner_database-1" {
  name           = "my-database"
  instance       = google_spanner_instance.spanner_instance.name
  ddl = [
    "CREATE TABLE example (id INT64 NOT NULL, name STRING(100)) PRIMARY KEY (id)"
  ]
}
