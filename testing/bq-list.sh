PROJECT_ID="spanner-gke-443910"
DATASET_ID="audit_service_dataset"

# List all tables in the dataset
TABLES=$(bq ls --dataset_id=$PROJECT_ID:$DATASET_ID | awk 'NR>2 {print $1}')

# Loop through tables and display schema
for TABLE in $TABLES; do
  echo "Schema for table: $TABLE"
  bq show --format=prettyjson $PROJECT_ID:$DATASET_ID.$TABLE | jq '.schema.fields'
  echo "--------------------------------------------"
done
