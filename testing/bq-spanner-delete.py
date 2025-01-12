import subprocess

# Function to execute shell commands and capture output
def run_command(command):
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error executing command: {command}")
        print(f"Error: {result.stderr}")
    else:
        print(f"Success: {command}")
        print(f"Output: {result.stdout}")

def delete_data():
    # Default values
    default_project_id = "spanner-gke-443910"
    default_dataset_id = "audit_service_dataset"
    default_spanner_instance_id = "sample-instance"
    default_spanner_database_id = "audit-db"
    default_table_name = "payment_audit_trail_changelog"
    default_psp_table_name = "payment_audit_trail_psp"
    
    # Prompt for user input or use defaults
    project_id = input(f"Enter the Google Cloud project ID (default: {default_project_id}): ") or default_project_id
    dataset_id = input(f"Enter the BigQuery dataset ID (default: {default_dataset_id}): ") or default_dataset_id
    spanner_instance_id = input(f"Enter the Spanner instance ID (default: {default_spanner_instance_id}): ") or default_spanner_instance_id
    spanner_database_id = input(f"Enter the Spanner database ID (default: {default_spanner_database_id}): ") or default_spanner_database_id
    table_name = input(f"Enter the BigQuery table name to delete (default: {default_table_name}): ") or default_table_name
    psp_table_name = input(f"Enter the Spanner table to delete from (default: {default_psp_table_name}): ") or default_psp_table_name

    # # Step 1: Create a temporary table in BigQuery
    # print("Step 1: Creating temporary table in BigQuery...")
    # create_temp_table_command = f"""
    # bq query --nouse_legacy_sql \
    # --destination_table="{dataset_id}.temp_table" \
    # "SELECT * FROM `{project_id}.{dataset_id}.{table_name}` WHERE FALSE"
    # """
    # run_command(create_temp_table_command)

    # # Step 2: Drop the original table in BigQuery
    # print("Step 2: Dropping the original table in BigQuery...")
    # drop_table_command = f"bq rm -f {project_id}:{dataset_id}.{table_name}"
    # run_command(drop_table_command)

    # # Step 3: Rename the temporary table to the original table name in BigQuery
    # print("Step 3: Renaming the temporary table...")
    # rename_table_command = f"bq cp {project_id}:{dataset_id}.temp_table {project_id}:{dataset_id}.{table_name}"
    # run_command(rename_table_command)

    # # Step 4: Remove the temporary table in BigQuery
    # print("Step 4: Removing the temporary table in BigQuery...")
    # remove_temp_table_command = f"bq rm -f {project_id}:{dataset_id}.temp_table"
    # run_command(remove_temp_table_command)

    # Step 5: Delete all rows from the Spanner table
    print("Step 5: Deleting all rows from Spanner...")
    spanner_delete_command = f"""
    gcloud spanner databases execute-sql {spanner_database_id} \
    --instance={spanner_instance_id} \
    --project={project_id} \
    --sql="DELETE FROM {psp_table_name} WHERE TRUE"
    """
    run_command(spanner_delete_command)

    print("\nData deletion process completed.")

if __name__ == "__main__":
    delete_data()
