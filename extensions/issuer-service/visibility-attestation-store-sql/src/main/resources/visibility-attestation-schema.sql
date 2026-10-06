CREATE TABLE IF NOT EXISTS visibility_attestation
(
    id         varchar default gen_random_uuid() not null
        constraint visibility_attestations_pk primary key,
    holder_id  varchar                           not null,
    properties JSON                              not null
);