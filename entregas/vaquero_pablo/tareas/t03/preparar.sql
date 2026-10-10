\set ON_ERROR_STOP on
CREATE EXTENSION IF NOT EXISTS citus;
SELECT citus_set_coordinator_host('coordinador', 5432);
SELECT citus_add_node('worker1', 5432);
SELECT citus_add_node('worker2', 5432);

-- Un cliente puede tener varios movimientos; todos usan la misma llave de reparto.
CREATE TABLE movimientos (
    cliente_id integer NOT NULL,
    movimiento_id integer NOT NULL,
    importe numeric(10,2) NOT NULL,
    PRIMARY KEY (cliente_id, movimiento_id)
);

SELECT create_distributed_table(
    'movimientos', 'cliente_id',
    colocate_with => 'none', shard_count => 6
);

INSERT INTO movimientos (cliente_id, movimiento_id, importe)
SELECT cliente, movimiento, cliente * 10 + movimiento
FROM generate_series(1, 12) AS cliente
CROSS JOIN generate_series(1, 2) AS movimiento;
