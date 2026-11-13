#!/bin/bash
set -e

echo "Inserting rows into participant_id table"

psql -v ON_ERROR_STOP=1 -h $DB_FQDN --username $POSTGRES_USER --dbname $DB_NAME -c "INSERT INTO participant_id (id, email, name) VALUES ('did:web:provider-identityhub:api:did', 'provider@example.com', 'provider');"
psql -v ON_ERROR_STOP=1 -h $DB_FQDN --username $POSTGRES_USER --dbname $DB_NAME -c "INSERT INTO participant_id (id, email, name) VALUES ('did:web:consumer-identityhub:api:did', 'consumer@example.com', 'consumer');"

echo "Rows inserted successfully"