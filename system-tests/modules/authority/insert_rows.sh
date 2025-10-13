#!/bin/bash
set -e

echo "Inserting rows into participant_id table"

psql -v ON_ERROR_STOP=1 -h $DB_FQDN --username $POSTGRES_USER --dbname $DB_NAME -c "INSERT INTO participant_id (id, email) VALUES ('did:web:provider-identityhub%3A8383:api:did', 'consumer@example.com');"
psql -v ON_ERROR_STOP=1 -h $DB_FQDN --username $POSTGRES_USER --dbname $DB_NAME -c "INSERT INTO participant_id (id, email) VALUES ('did:web:consumer-identityhub%3A8383:api:did', 'provider@example.com');"

echo "Rows inserted successfully"