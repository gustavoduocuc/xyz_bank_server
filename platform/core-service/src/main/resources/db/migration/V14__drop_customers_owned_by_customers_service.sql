-- Customer data now belongs to customers-service (its own customers schema). The customer_id
-- columns stay as plain identifiers; every foreign key to customers is dropped first. They
-- were created unnamed, so they are found through the catalog.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f' AND confrelid = 'public.customers'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.table_name, fk.conname);
    END LOOP;
END $$;

DROP TABLE customers;
