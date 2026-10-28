-- D-17.2-02: one product per release; the PK stays because client_deployment's composite FK targets it (D-15-15).
ALTER TABLE release_product ADD CONSTRAINT release_product_one_per_release UNIQUE (release_id);
