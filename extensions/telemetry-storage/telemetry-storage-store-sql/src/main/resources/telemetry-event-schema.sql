CREATE TABLE IF NOT EXISTS participant_id
(
    id        varchar   not null
        constraint participant_id_pk
            primary key,
    email     varchar   not null,
    name varchar   null, -- this should be not null, but since this is a new field and the table might be deployed already, we need to avoid schema validation issues.
    timestamp timestamp default now() not null
);

CREATE TABLE IF NOT EXISTS report
(
    id              serial    not null
        constraint report_pk
            primary key,
    csv_name        varchar   not null,
    csv_link        varchar   not null,
    participant_did varchar   not null
        constraint report_participant_did_fk
            references participant_id (id)
            on delete cascade,
    timestamp       timestamp default now() not null
);

CREATE TABLE IF NOT EXISTS telemetry_event
(
id              varchar   not null
        constraint telemetry_event_pk
            primary key,
    contract_id     varchar   not null,
    participant_did varchar   not null
        constraint telemetry_event_participant_did_fk
            references participant_id (id)
            on delete cascade,
    response_status_code int       not null,
    msg_size        int       not null,
    csv_id          int       null
        constraint telemetry_event_csv_id_fk
            references report (id)
            on delete set null,
    timestamp       timestamp default now() not null,
    constraint unique_contract_participant_timestamp
        unique (contract_id, participant_did, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_event_participant_timestamp_contract
    ON telemetry_event (participant_did, timestamp, contract_id);

