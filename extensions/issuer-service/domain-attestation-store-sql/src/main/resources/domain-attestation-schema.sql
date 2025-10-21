CREATE TABLE IF NOT EXISTS domain_attestation
(
    holder_id             varchar                             not null,
    domain                  varchar                             not null,
    id                    varchar   default gen_random_uuid() not null,
    constraint domain_attestations_pk primary key(id)
    );

