create table document (
    id         uuid primary key,
    title      varchar(255)   not null,
    content    varchar(10000),
    owner_id   varchar(255)   not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);
