import pandas as pd

# Load data from both sources
spanner_data = pd.read_csv("spanner_data.csv")
bigquery_data = pd.read_csv("bigquery_data.csv")

# Find rows in Spanner that are not in BigQuery based on PUID
missing_in_bq = spanner_data[~spanner_data['PUID'].isin(bigquery_data['PUID'])]

# Print the missing PUID values
print("PUIDs missing in BigQuery:")
print(missing_in_bq['PUID'])
