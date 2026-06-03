CREATE TABLE location
(
    id         UUID         NOT NULL,
    city       VARCHAR(255) NOT NULL,
    geo        GEOGRAPHY,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT location_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_location_city ON location (city);
