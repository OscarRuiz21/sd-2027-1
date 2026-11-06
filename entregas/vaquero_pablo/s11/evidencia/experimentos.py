"""Experimentos reales del lab S11. Ejecutar cada etapa una vez y en el orden del README."""
from pathlib import Path
import os, sys, subprocess, json, time, urllib.request, urllib.error, statistics, math, uuid, base64
from datetime import datetime, timezone

EV = Path(__file__).resolve().parent
APP = Path(os.environ.get('S11_APP', str(EV.parents[4] / 'mexi-banco-s11'))).resolve()
BASE = os.environ.get('S11_URL', 'http://localhost:18081')
ENV = dict(os.environ, PUERTO_GATEWAY='18081')
ANA, BETO, CARLA = ['00218000000000000' + str(i) for i in (1,2,3)]

def cmd(*args):
    r = subprocess.run(['docker','compose','-p','mexibanco-s11-pablo','-f',str(APP/'docker-compose.yml'),*args],
                       env=ENV, text=True, capture_output=True, check=True)
    return r.stdout

def redis(*args): return cmd('exec','-T','redis','redis-cli','--raw',*args).strip()
def mongo(js):
    return json.loads(cmd('exec','-T','mongo','mongosh','--quiet','notificacion','--eval',
                         'JSON.stringify('+js+')').strip())
def sql(q): return cmd('exec','-T','db','psql','-X','-t','-A','-U','mexibanco','-d','cuenta','-c',q).strip()
def logs(): return cmd('logs','--no-color','cuenta')
def pgreads(): return logs().count('leida de Postgres')
def stats():
    d = dict(l.split(':',1) for l in redis('INFO','stats').splitlines() if ':' in l)
    h,m = int(d['keyspace_hits']),int(d['keyspace_misses'])
    return dict(hits=h, misses=m, ratio=h/(h+m) if h+m else None)
def http(path, body=None, origin=BASE, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(origin+path, data=data, headers={'Content-Type':'application/json',**(headers or {})})
    start=time.perf_counter()
    try: r=urllib.request.urlopen(req, timeout=50)
    except urllib.error.HTTPError as e: r=e
    raw=r.read().decode()
    try: data=json.loads(raw)
    except ValueError: data=raw
    return dict(status=r.code, ms=(time.perf_counter()-start)*1000, instancia=r.headers.get('X-Instancia'), body=data)
def get(clabe=ANA):
    r=http('/api/cuentas/'+clabe)
    assert r['status']==200,r
    return r
def transfer(amount=1, destino=BETO):
    r=http('/api/transferencias',dict(claveOrigen=ANA,claveDestino=destino,monto=amount))
    assert r['status']==201,r
    return r
def wait_ready():
    for _ in range(100):
        try:
            r=http('/api/cuentas/'+ANA)
            if r['status'] in (200,404): return
        except Exception: pass
        time.sleep(2)
    raise RuntimeError('Gateway o servicios no listos')
def save(name,d):
    d=dict(fecha=datetime.now(timezone.utc).isoformat(),**d)
    (EV/(name+'.json')).write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(d,ensure_ascii=False,indent=2),flush=True)

def base():
    wait_ready()
    for clabe,titular,saldo in [(ANA,'Ana',1000),(BETO,'Beto',500),(CARLA,'Carla',300)]:
        r=http('/api/cuentas',dict(clabe=clabe,titular=titular,saldoInicial=saldo)); assert r['status']==201,r
    before=pgreads(); respuestas=[get() for _ in range(10)]; after=pgreads()
    t=transfer(10); time.sleep(2)
    d=dict(respuestas=respuestas,lecturas_postgres=after-before,redis=stats(),avisos_mongo=mongo('db.avisos.countDocuments()'),
           transferencia=t,bandeja_postgres=http('/api/notificaciones?clabe='+ANA))
    assert d['lecturas_postgres']==10,d
    assert d['avisos_mongo']==0,d
    save('01-base',d)

def cache():
    wait_ready(); redis('DEL','cuentas::'+ANA)
    before=pgreads(); seis=[get() for _ in range(6)]; lecturas=pgreads()-before
    cache_json=json.loads(redis('GET','cuentas::'+ANA)); ttl=int(redis('TTL','cuentas::'+ANA))
    t=transfer(1); existe=int(redis('EXISTS','cuentas::'+ANA)); nuevo=get()
    assert lecturas==1,(lecturas,seis)
    assert len(set(r['instancia'] for r in seis))==3,seis
    assert len(set(r['body']['leidaEn'] for r in seis))==1,seis
    assert 0<ttl<=30 and existe==0,(ttl,existe)
    save('02-cache',dict(seis=seis,lecturas_postgres=lecturas,redis=cache_json,ttl=ttl,transferencia=t,existe_despues=existe,nuevo=nuevo))
    for nombre,escrituras in [('A',False),('B',True)]:
        redis('DEL','cuentas::'+ANA); redis('CONFIG','RESETSTAT'); before=pgreads(); respuestas=[]; ts=[]
        for i in range(100):
            respuestas.append(get())
            if escrituras and (i+1)%10==0: ts.append(transfer())
        contadores=stats(); lecturas=pgreads()-before
        # La saga tambien consulta las cuentas por HTTP. En B se cuentan sus lecturas y
        # accesos a Redis ademas de los cien GET del cliente; no asumir diez misses.
        assert lecturas>0,(nombre,lecturas)
        save('03-hit-ratio-'+nombre,dict(escenario=nombre,redis=contadores,lecturas_postgres=lecturas,respuestas=respuestas,transferencias=ts))

def latencias(tipo):
    wait_ready()
    for _ in range(6): get()
    for vuelta in (1,2,3):
        redis('DEL','cuentas::'+ANA); before=pgreads()
        respuestas=[get() for _ in range(100)]
        valores=sorted(r['ms'] for r in respuestas)
        lecturas=pgreads()-before
        assert lecturas==(100 if tipo=='sin' else 1),(tipo,lecturas)
        save(f'04-latencia-{tipo}-{vuelta}',dict(tipo=tipo,vuelta=vuelta,n=100,media_ms=statistics.mean(valores),
             mediana_ms=statistics.median(valores),p95_ms=valores[math.ceil(.95*len(valores))-1],lecturas_postgres=lecturas,muestras=respuestas))

def avisos():
    wait_ready()
    ts=[transfer(1),transfer(1,CARLA)]
    ts.extend(transfer(1) for _ in range(12))
    ids=[t['body']['id'] for t in ts]
    for _ in range(60):
        nuevos=mongo('db.avisos.countDocuments({transferenciaId:{$in:'+json.dumps(ids)+'}})')
        if nuevos==28: break
        time.sleep(1)
    assert nuevos==28,nuevos
    total=mongo('db.avisos.countDocuments()')
    formas=mongo('db.avisos.aggregate([{$group:{_id:"$canal",ejemplo:{$first:"$$ROOT"}}}]).toArray()')
    ultimos=http('/api/notificaciones/'+BETO)
    assert ultimos['status']==200 and len(ultimos['body'])==10,ultimos
    assert {f['_id'] for f in formas}=={'SMS','CORREO','PUSH'},formas
    consulta='db.avisos.find({clabe:"'+BETO+'"}).sort({fecha:-1}).limit(10)'
    save('05-mongo',dict(transferencias=ts,total=total,formas=formas,ultimos=ultimos,
         indices=mongo('db.avisos.getIndexes()'),indice=mongo(consulta+'.explain("executionStats")'),
         natural=mongo(consulta+'.hint({$natural:1}).explain("executionStats")')))

def duplicado():
    evento=dict(eventoId=str(uuid.uuid4()),tipo='TransferenciaRealizada',transferenciaId=99999,
                clabeOrigen=ANA,clabeDestino=BETO,monto=25,saldoOrigen=700,saldoDestino=825,fecha=datetime.now(timezone.utc).isoformat())
    auth=base64.b64encode(b'mexibanco:mexibanco').decode()
    counts=[]; publicaciones=[]
    for _ in range(2):
        r=http('/api/exchanges/%2F/transferencias/publish',dict(properties={},routing_key='transferencia.realizada',
             payload=json.dumps(evento),payload_encoding='string'),origin='http://localhost:15672',headers={'Authorization':'Basic '+auth})
        assert r['status']==200 and r['body']['routed'],r
        publicaciones.append(r)
        for intento in range(30):
            n=mongo('db.avisos.countDocuments({eventoId:"'+evento['eventoId']+'"})')
            if n==2: break
            time.sleep(.3)
        time.sleep(1); counts.append(n)
    assert counts==[2,2],counts
    save('06-duplicado',dict(evento=evento,publicaciones=publicaciones,avisos_despues_de_cada_publicacion=counts,
         logs=cmd('logs','--tail','100','notificacion')))

def viejo():
    redis('DEL','cuentas::'+ANA); anterior=get(); saldo=anterior['body']['saldo']
    sql("UPDATE cuenta SET saldo=5 WHERE clabe='"+ANA+"'")
    try:
        viejo=get()
        rechazo=http('/api/transferencias',dict(claveOrigen=ANA,claveDestino=BETO,monto=100))
        assert rechazo['status']!=201,rechazo
        assert float(sql("SELECT saldo FROM cuenta WHERE clabe='"+ANA+"'"))==5
        inicio=time.monotonic(); observaciones=[]
        for _ in range(9):
            r=get(); ttl=int(redis('TTL','cuentas::'+ANA))
            observaciones.append(dict(segundos=time.monotonic()-inicio,ttl=ttl,respuesta=r))
            if float(r['body']['saldo'])==5: break
            time.sleep(5)
        assert float(observaciones[-1]['respuesta']['body']['saldo'])==5,observaciones
        save('07-dato-viejo',dict(saldo_real_previo=saldo,cache_inicial=anterior,respuesta_vieja=viejo,
             transferencia_rechazada=rechazo,observaciones=observaciones,segundos_hasta_refrescar=observaciones[-1]['segundos']))
    finally:
        sql("UPDATE cuenta SET saldo="+str(saldo)+" WHERE clabe='"+ANA+"'")
        redis('DEL','cuentas::'+ANA)
        print('Saldo de prueba restaurado en PostgreSQL; cache invalidada.',flush=True)

if __name__=='__main__':
    etapa=sys.argv[1]
    if etapa=='base': base()
    elif etapa=='cache': cache()
    elif etapa in ('sin','con'): latencias(etapa)
    elif etapa=='mongo': avisos()
    elif etapa=='duplicado': duplicado()
    elif etapa=='viejo': viejo()
    else: raise SystemExit('Etapa desconocida')
