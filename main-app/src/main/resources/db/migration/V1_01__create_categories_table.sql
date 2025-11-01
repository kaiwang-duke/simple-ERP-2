-- V1_01__create_categories_table.sql

CREATE TABLE  IF NOT EXISTS public.categories (
       id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
       name varchar(255) NOT NULL,
       cn_name varchar(255) NOT NULL,
       parent_id integer,
       is_seasonal boolean NOT NULL DEFAULT false,

       start_selling_month int CHECK (start_selling_month BETWEEN 1 AND 12),
       stop_selling_month int CHECK (stop_selling_month BETWEEN 1 AND 12),
       purchase_month int CHECK (purchase_month BETWEEN 1 AND 12),

       CONSTRAINT categories_parent_id_fkey FOREIGN KEY (parent_id)
           REFERENCES public.categories (id),

       CONSTRAINT chk_seasonal_months CHECK (
            NOT is_seasonal
           OR
           (
                   purchase_month IS NOT NULL
                   AND start_selling_month IS NOT NULL
                   AND stop_selling_month IS NOT NULL
               )
           )
);

-- PostgreSQL 14 +  (works on 15/16 too), unique index on parent_id and name
CREATE UNIQUE INDEX uq_category_path
    ON categories ( parent_id, lower(trim(name)) )
    NULLS NOT DISTINCT;


CREATE TABLE vm_lease (
    vm_name     varchar(100)  PRIMARY KEY,
    zone varchar(50) NOT NULL DEFAULT 'us-east1',
    user_email  varchar(255)  NOT NULL,   -- keep for quick access
    started_at  timestamptz   NOT NULL DEFAULT now(),
    expires_at  timestamptz   NOT NULL
);

CREATE INDEX idx_vm_lease_expires
    ON vm_lease (zone, expires_at);


-- play table
CREATE TABLE contact (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255),
                         phone VARCHAR(255)
);

