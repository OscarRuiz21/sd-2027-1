#!/usr/bin/env python3
"""Demuestra sharding y expansión de Citus; conserva comandos y salidas reales."""
import argparse
import json
import shlex
import subprocess
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--salida", default="resultados", help="Carpeta para los registros")
args = parser.parse_args()
out = ROOT / args.salida
out.mkdir(parents=True, exist_ok=True)
transcript = out / "ejecucion.txt"
if transcript.exists():
    raise SystemExit("La salida ya existe. Usa otra carpeta con --salida para conservarla.")


def record(text):
    with transcript.open("a", encoding="utf-8") as f:
        f.write(text + "\n")


def command(argv, stdin=None):
    record("\n$ " + shlex.join(argv))
    if stdin:
        record(stdin.strip())
    result = subprocess.run(argv, cwd=ROOT, input=stdin, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    record(result.stdout.rstrip())
    if result.returncode:
        raise RuntimeError(f"Falló el comando; consulta {transcript}")
    return result.stdout.strip()


def sql(query, node="coordinador", tuples=False):
    argv = ["docker", "compose", "exec", "-T", node, "psql",
            "-X", "-U", "postgres", "-d", "t03", "-v", "ON_ERROR_STOP=1"]
    argv += ["-At"] if tuples else ["-P", "pager=off"]
    return command(argv, query)


def rows(query, node="coordinador"):
    return json.loads(sql(
        "SELECT COALESCE(json_agg(fila), '[]'::json) FROM (" + query +
        ") AS fila;", node, tuples=True))


def save(name, value):
    (out / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")


def snapshot(label):
    shards = rows("""
        SELECT shardid, shard_name, nodename, nodeport
        FROM citus_shards WHERE table_name = 'movimientos'::regclass
        ORDER BY shardid
    """)
    logical = rows("SELECT * FROM movimientos ORDER BY cliente_id, movimiento_id")
    # json_agg puede combinar resultados parciales en otro orden; comparamos conjuntos ordenados.
    logical.sort(key=lambda r: (r["cliente_id"], r["movimiento_id"]))
    shards.sort(key=lambda s: s["shardid"])
    mapping = rows("""
        SELECT cliente_id,
               get_shard_id_for_distribution_column('movimientos', cliente_id) AS shardid
        FROM generate_series(1, 12) AS clientes(cliente_id) ORDER BY cliente_id
    """)
    mapping.sort(key=lambda r: r["cliente_id"])
    physical = []
    for shard in shards:
        # El nombre procede de citus_shards; se cita como identificador SQL.
        name = shard["shard_name"]
        qualified = ".".join('"' + p.replace('"', '""') + '"' for p in name.split("."))
        data = rows(f"SELECT * FROM {qualified} ORDER BY cliente_id, movimiento_id",
                    shard["nodename"])
        physical.extend(dict(row, shardid=shard["shardid"], nodo=shard["nodename"])
                        for row in data)
    physical.sort(key=lambda r: (r["cliente_id"], r["movimiento_id"]))
    expected = [{"cliente_id": c, "movimiento_id": m, "importe": c * 10 + m}
                for c in range(1, 13) for m in range(1, 3)]
    actual = [{k: r[k] for k in ("cliente_id", "movimiento_id", "importe")}
              for r in physical]
    assert logical == expected, "La consulta lógica cambió las filas."
    assert actual == expected, "Faltan filas físicas, sobran copias o cambió un importe."
    by_client = {r["cliente_id"]: r["shardid"] for r in mapping}
    assert all(r["shardid"] == by_client[r["cliente_id"]] for r in physical)
    assert len(shards) == 6
    assert sum(r["importe"] for r in physical) == 1596
    result = {"shards": shards, "filas": physical, "mapa_clientes": mapping,
              "conteo": len(physical), "suma_importes": 1596}
    save(label + ".json", result)
    record(f"VERIFICADO {label}: 24 filas físicas, sin duplicados, suma 1596.00.")
    return result


record("T03 · Pablo Vaquero · inicio " + datetime.now().astimezone().isoformat())
command(["docker", "compose", "up", "-d", "--wait"])
version = sql("SELECT version(); SELECT citus_version();")
save("versiones.json", {"versiones": version, "inicio": datetime.now().astimezone().isoformat()})
sql((ROOT / "preparar.sql").read_text(encoding="utf-8"))
sql("""SELECT logicalrelid, partmethod FROM pg_dist_partition;
SELECT shardid, shardminvalue, shardmaxvalue FROM pg_dist_shard
WHERE logicalrelid = 'movimientos'::regclass ORDER BY shardid;""")
before = snapshot("01-dos-workers")
assert sorted({s["nodename"] for s in before["shards"]}) == ["worker1", "worker2"]
assert all(sum(s["nodename"] == node for s in before["shards"]) == 3
           for node in ("worker1", "worker2"))

explain_one = sql("EXPLAIN (COSTS OFF) SELECT * FROM movimientos WHERE cliente_id = 7;")
explain_all = sql("EXPLAIN (COSTS OFF) SELECT sum(importe) FROM movimientos;")
assert "Task Count: 1" in explain_one
assert "Task Count: 6" in explain_all
record("VERIFICADO ruteo: 1 tarea con cliente_id = 7; 6 tareas para el total.")

command(["docker", "compose", "--profile", "escala", "up", "-d", "--wait", "worker3"])
sql("SELECT citus_add_node('worker3', 5432);")
registered = snapshot("02-nodo-agregado")
assert registered["shards"] == before["shards"], "Registrar el nodo movió datos."
record("VERIFICADO alta: worker3 registrado, cero shards recibidos todavía.")

sql("""SELECT citus_rebalance_start(rebalance_strategy := 'by_shard_count');
SELECT citus_rebalance_wait();
SELECT state FROM citus_rebalance_status();""")
after = snapshot("03-rebalanceado")
assert all(sum(s["nodename"] == node for s in after["shards"]) == 2
           for node in ("worker1", "worker2", "worker3"))
old_nodes = {s["shardid"]: s["nodename"] for s in before["shards"]}
moved = [s["shardid"] for s in after["shards"] if s["nodename"] != old_nodes[s["shardid"]]]
assert len(moved) == 2
assert before["mapa_clientes"] == after["mapa_clientes"], "Cambió el hash→shard."
summary = {"filas": 24, "shards": 6, "workers_antes": 2, "workers_despues": 3,
           "shards_movidos": moved,
           "filas_movidas": sum(r["shardid"] in moved for r in before["filas"]),
           "suma_antes_y_despues": 1596,
           "fin": datetime.now().astimezone().isoformat()}
save("resumen.json", summary)
record("VERIFICACIONES COMPLETAS\n" + json.dumps(summary, ensure_ascii=False, indent=2))
command(["docker", "compose", "--profile", "escala", "ps"])
print(json.dumps(summary, ensure_ascii=False, indent=2))
print(f"Evidencias: {out}")
