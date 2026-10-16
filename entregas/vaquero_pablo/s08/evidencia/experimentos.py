"""Pruebas reales por el gateway. Usa solo las cuentas reservadas para este lab."""
import concurrent.futures
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidencia'
COMPOSE = ['docker', 'compose', '-f', 'docker-compose.yml', '-f', 'evidencia/compose-lab.yml']
BASE = 'http://localhost:8080/api'

def docker(*args, env=None):
    p = subprocess.run(COMPOSE + list(args), cwd=ROOT, env={**os.environ, **(env or {})},
                       text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True)
    return p.stdout

def http(path, body=None):
    start = time.monotonic()
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            status, data = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, data = e.code, e.read()
    return {'http': status, 'ms': round((time.monotonic()-start)*1000, 2),
            'body': json.loads(data) if data else None}

def wait_account(clabe, expected=None, seconds=150):
    until = time.monotonic()+seconds
    while time.monotonic() < until:
        try:
            r = http('/cuentas/'+clabe)
            if r['http'] == 200 and (expected is None or r['body']['saldo'] == expected):
                return r
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError('La cuenta no alcanzo el saldo esperado: '+clabe)

def create(clabe, name, balance):
    r = http('/cuentas', {'clabe': clabe, 'titular': name, 'saldoInicial': balance})
    assert r['http'] == 201, r
    return r

def sql(db, query):
    return docker('exec', '-T', 'db', 'psql', '-U', 'mexibanco', '-d', db, '-P', 'pager=off', '-c', query)

def save(name, data):
    data['fecha_local'] = datetime.now(ZoneInfo('America/Mexico_City')).isoformat()
    (OUT / (name+'.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(data, ensure_ascii=False, indent=2), flush=True)

def baseline():
    a, b = '002180000000008101', '002180000000008102'
    create(a, 'S08 origen base', 1000)
    create(b, 'S08 destino base', 500)
    data = {'antes': [http('/cuentas/'+a), http('/cuentas/'+b)]}
    with concurrent.futures.ThreadPoolExecutor() as pool:
        task = pool.submit(http, '/transferencias', {'claveOrigen':a,'claveDestino':b,'monto':200})
        data['durante_pausa'] = wait_account(a, 800, 15)
        data['stop'] = docker('stop', 'cuenta')
        data['respuesta'] = task.result()
    data['start'] = docker('start', 'cuenta')
    data['despues'] = [wait_account(a, 800), wait_account(b, 500)]
    data['tabla_transferencia'] = sql('transferencia','select * from transferencia;')
    assert sum(r['body']['saldo'] for r in data['antes']) == 1500
    assert sum(r['body']['saldo'] for r in data['despues']) == 1300
    save('01-problema-original', data)

def configure(pause=0, fail=''):
    docker('up', '-d', '--no-deps', '--wait', '--wait-timeout', '120', 'transferencia',
           env={'PAUSA_ENTRE_CARGO_Y_ABONO_MS':str(pause), 'FALLAR_ABONO_CLABE':fail})
    until = time.monotonic()+90
    while time.monotonic() < until:
        try:
            if http('/transferencias/circuito')['http'] == 200:
                return
        except (OSError,ValueError):
            pass
        time.sleep(1)
    raise AssertionError('transferencia no aparece en el gateway')

def table(t):
    return sql('transferencia',f"select id, estado, monto, detalle, historial from transferencia where id={int(t['body']['id'])};")

def sagas():
    a,b='002180000000008201','002180000000008202'
    configure(fail=b)
    create(a,'S08 compensacion origen',1000)
    create(b,'S08 compensacion destino',500)
    data={'variable':f'FALLAR_ABONO_CLABE={b}', 'antes':[http('/cuentas/'+a),http('/cuentas/'+b)]}
    r=http('/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':200})
    data['respuesta']=r
    data['despues']=[http('/cuentas/'+a),http('/cuentas/'+b)]
    data['tabla_transferencia']=table(r)
    data['movimientos']=sql('movimiento',f"select clabe_cuenta,tipo,monto,saldo_resultante,referencia from movimiento where clabe_cuenta in ('{a}','{b}') order by id;")
    assert r['http']==409 and r['body']['estado']=='COMPENSADA',r
    assert [x['body']['saldo'] for x in data['despues']]==[1000,500]
    save('03-compensacion',data)
    a,b='002180000000008203','002180000000008204'
    create(a,'S08 exito origen',1000)
    create(b,'S08 exito destino',500)
    r=http('/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':200})
    data={'respuesta':r,'despues':[http('/cuentas/'+a),http('/cuentas/'+b)],'tabla_transferencia':table(r)}
    assert r['http']==201 and r['body']['estado']=='COMPLETADA',r
    assert [x['body']['saldo'] for x in data['despues']]==[800,700]
    save('02-transferencia-exitosa',data)

def pending():
    a,b='002180000000008301','002180000000008302'
    configure(pause=20000)
    create(a,'S08 pendiente origen',1000)
    create(b,'S08 pendiente destino',500)
    data={'antes':[http('/cuentas/'+a),http('/cuentas/'+b)]}
    with concurrent.futures.ThreadPoolExecutor() as pool:
        task=pool.submit(http,'/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':200})
        data['durante_pausa']=wait_account(a,800,15)
        data['estado_durante_pausa']=sql('transferencia',f"select id,estado,detalle from transferencia where clave_origen='{a}';")
        data['stop']=docker('stop','cuenta')
        r=task.result()
    data['respuesta']=r
    data['tabla_transferencia']=table(r)
    assert r['http']==202 and r['body']['estado']=='COMPENSACION_PENDIENTE',r
    data['start']=docker('start','cuenta')
    data['despues']=[wait_account(a,800),wait_account(b,500)]
    configure()
    data['tras_reiniciar_transferencia']=http('/transferencias/'+str(r['body']['id']))
    assert data['tras_reiniciar_transferencia']['body']['estado']=='COMPENSACION_PENDIENTE'
    save('04-compensacion-pendiente',data)

def breaker():
    a,b='002180000000008203','002180000000008204'
    data={'cerrado':http('/transferencias/circuito')}
    data['stop']=docker('stop','cuenta')
    data['fallas']=[]
    for _ in range(3):
        data['fallas'].append(http('/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':1}))
    data['abierto']=http('/transferencias/circuito')
    assert data['abierto']['body']['estado']=='ABIERTO',data
    data['rechazos']=[http('/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':1}) for _ in range(3)]
    data['tras_rechazos']=http('/transferencias/circuito')
    assert data['abierto']['body']['llamadasPermitidas']==data['tras_rechazos']['body']['llamadasPermitidas']
    assert all(x['http']==503 for x in data['rechazos']),data
    data['start']=docker('start','cuenta')
    wait_account(a)
    data['pruebas_recuperacion']=[]
    for _ in range(4):
        time.sleep(16)
        r=http('/transferencias',{'claveOrigen':a,'claveDestino':b,'monto':1})
        data['pruebas_recuperacion'].append(r)
        if r['http']==201:
            break
        assert r['body']['estado']=='NO_DISPONIBLE',r
    assert r['http']==201,r
    data['recuperado']=http('/transferencias/circuito')
    transitions=[x['hacia'] for x in data['recuperado']['body']['cambios']]
    assert 'SEMIABIERTO' in transitions and transitions[-1]=='CERRADO'
    data['logs']=docker('logs','--no-color','transferencia')
    data['logs']='\n'.join(x for x in data['logs'].splitlines() if 'CIRCUITO cuenta' in x or 'SAGA ' in x)
    save('05-circuit-breaker',data)

if __name__ == '__main__':
    {'base':baseline,'sagas':sagas,'pendiente':pending,'circuito':breaker}[sys.argv[1]]()
