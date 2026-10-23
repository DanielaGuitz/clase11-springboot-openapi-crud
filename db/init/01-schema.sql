-- Schema owned by SQL (not by Hibernate). MariaDB runs every file in
-- /docker-entrypoint-initdb.d the FIRST time the container starts (empty volume).
CREATE TABLE IF NOT EXISTS customers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    full_name   VARCHAR(120) NOT NULL,
    meter_code  VARCHAR(20)  NOT NULL,
    address     VARCHAR(200) NOT NULL,
    email       VARCHAR(120) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_meter_code UNIQUE (meter_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
