"""Experimentos reales S11. Ejecutar desde la carpeta de la base completa."""
from pathlib import Path
import subprocess, requests, time, json, statistics, os, sys
ROOT=Path(__file__).resolve().parent
E=ROOT/'evidencias'; E.mkdir(exist_ok=True)
URL='http://localhost:18011/api'
A='002180000000000001'; B='002180000000000002'; C='002180000000000003'
s=requests.Session()
def save(name,value):
    (E/(name+'.json')).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
def dc(*args,env=None):
    r=subprocess.run(['docker','compose',*args],cwd=ROOT,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,encoding='utf-8',errors='replace')
    if r.returncode: raise RuntimeError(r.stdout)
    return r.stdout.strip()
def redis(*args): return dc('exec','-T','redis','redis-cli','--raw',*args)
def mongo(js): return dc('exec','-T','mongo','mongosh','--quiet','notificacion','--eval',js)
def sql(q): return dc('exec','-T','db','psql','-U','mexibanco','-d','cuenta','-Atc',q)
def get(clabe=A):
    t=time.perf_counter(); r=s.get(URL+'/cuentas/'+clabe,timeout=20)
    r.raise_for_status()
    return {'ms':(time.perf_counter()-t)*1000,'status':r.status_code,'instancia':r.headers.get('X-Instancia'),'body':r.json()}
def transfer(amount=1,dest=B):
    r=s.post(URL+'/transferencias',json={'claveOrigen':A,'claveDestino':dest,'monto':amount},timeout=30)
    return {'status':r.status_code,'body':r.json()}
def wait_ready():
    for _ in range(180):
        try:
            r=s.get(URL+'/cuentas/'+A,timeout=3)
            if r.status_code in (200,404): return
        except requests.RequestException: pass
        time.sleep(2)
    raise RuntimeError('Gateway/cuenta no disponibles')
def mode(value):
    env=os.environ.copy(); env['CACHE_TIPO']=value
    dc('up','-d','--no-deps','cuenta',env=env)
    for _ in range(120):
        states=[json.loads(line) for line in dc('ps','--format','json','cuenta').splitlines() if line.strip()]
        if len(states)==3 and all(row.get('Health')=='healthy' for row in states): break
        time.sleep(2)
    else: raise RuntimeError('Las tres replicas de cuenta no estan healthy')
    time.sleep(15); wait_ready()
def logs(): return dc('logs','--no-color','cuenta')
def pgcount(): return logs().count('leida de Postgres')
def stats():
    return {k:int(v) for line in redis('INFO','stats').splitlines() if ':' in line for k,v in [line.split(':',1)] if k in ('keyspace_hits','keyspace_misses')}
def run100():
    before=pgcount(); rows=[get() for _ in range(100)]; ms=[r['ms'] for r in rows]
    return {'consultas':rows,'promedio_ms':statistics.mean(ms),'mediana_ms':statistics.median(ms),'p95_ms':sorted(ms)[94],'lecturas_postgres':pgcount()-before}
phase=sys.argv[1]
if phase=='baseline':
    wait_ready()
    for clabe,name,balance in [(A,'Ana',1000),(B,'Beto',500),(C,'Carla',300)]:
        r=s.post(URL+'/cuentas',json={'clabe':clabe,'titular':name,'saldoInicial':balance},timeout=30)
        assert r.status_code in (201,409),(r.status_code,r.text)
    mode('none')
    before=pgcount(); rows=[get() for _ in range(10)]
    save('00-punto-partida',{'consultas':rows,'lecturas_postgres':pgcount()-before,'redis':stats(),'mongo':mongo('db.avisos.countDocuments()'),'nota':'Codigo S11 con CACHE_TIPO=none; aun no se realizan transferencias.'})
    results=[]
    for _ in range(3): results.append(run100())
    save('01-latencia-sin-cache',results)
    mode('redis')
elif phase=='cache':
    redis('DEL','cuentas::'+A); before=pgcount()
    rows=[get() for _ in range(6)]
    save('02-cache-compartida',{'consultas':rows,'lecturas_postgres':pgcount()-before,'valor':redis('GET','cuentas::'+A),'ttl':redis('TTL','cuentas::'+A)})
    with (E/'03-redis-monitor.txt').open('w',encoding='utf-8') as monitor_file:
        monitor=subprocess.Popen(['docker','compose','exec','-T','redis','timeout','8','redis-cli','MONITOR'],cwd=ROOT,stdout=monitor_file,stderr=subprocess.STDOUT)
        time.sleep(1)
        result=transfer(200); assert result['status']==201,result
        save('03-invalidacion',{'transferencia':result,'existe_despues':redis('EXISTS','cuentas::'+A),'siguiente_get':get()})
        monitor.wait(timeout=15)
    for scenario in ('A','B'):
        redis('DEL','cuentas::'+A); redis('CONFIG','RESETSTAT'); before=pgcount(); rows=[]; transfers=[]
        for i in range(100):
            rows.append(get())
            if scenario=='B' and (i+1)%10==0:
                r=transfer(); assert r['status']==201,r
                transfers.append(r)
        counters=stats(); total=sum(counters.values())
        save('04-hit-ratio-'+scenario,{'redis':counters,'ratio':counters['keyspace_hits']/total,'lecturas_postgres':pgcount()-before,'consultas':rows,'transferencias':transfers})
    save('05-latencia-cache',[run100() for _ in range(3)])
elif phase=='mongo':
    result=transfer(5,C); assert result['status']==201,result
    for _ in range(12):
        result=transfer(); assert result['status']==201,result
    time.sleep(5)
    save('06-avisos',{'documentos':mongo('EJSON.stringify(db.avisos.find().sort({fecha:1}).toArray())'),'sms':mongo('EJSON.stringify(db.avisos.find({telefono:{$exists:true}},{clabe:1,canal:1,telefono:1}).toArray())'),'endpoint':s.get(URL+'/notificaciones/'+B,timeout=20).json(),'indices':mongo('EJSON.stringify(db.avisos.getIndexes())')})
    for name,hint in [('indice',''),('natural','.hint({$natural:1})')]:
        save('07-explain-'+name,json.loads(mongo('EJSON.stringify(db.avisos.find({clabe:"'+B+'"}).sort({fecha:-1}).limit(10)'+hint+'.explain("executionStats"))')))
    event={'eventoId':'11111111-2222-4333-8444-555555555555','tipo':'TransferenciaRealizada','transferenciaId':9001,'clabeOrigen':'002180000000000091','clabeDestino':'002180000000000092','monto':25,'saldoOrigen':475,'saldoDestino':525,'fecha':'2026-10-31T13:00:00Z'}
    payload={'properties':{'delivery_mode':2,'content_type':'application/json'},'routing_key':'transferencia.realizada','payload_encoding':'string','payload':json.dumps(event)}
    replies=[]
    for _ in range(2):
        r=s.post('http://localhost:15672/api/exchanges/%2F/transferencias/publish',auth=('mexibanco','mexibanco'),json=payload,timeout=15);r.raise_for_status();replies.append(r.json())
    time.sleep(4)
    count=mongo('db.avisos.countDocuments({eventoId:"'+event['eventoId']+'"})');assert count=='2',count
    save('08-duplicado',{'publicaciones':replies,'avisos':count,'log':dc('logs','--no-color','notificacion')})
elif phase=='viejo':
    redis('DEL','cuentas::'+A); cached=get(); original=sql("select saldo from cuenta where clabe='"+A+"'")
    rows=[]; started=time.perf_counter()
    try:
        sql("update cuenta set saldo=5 where clabe='"+A+"'")
        attempt=transfer(100)
        for i in range(8):
            rows.append({'segundos':time.perf_counter()-started,'ttl':redis('TTL','cuentas::'+A),'consulta':get()})
            if i<7: time.sleep(5)
    finally:
        sql("update cuenta set saldo="+original+" where clabe='"+A+"'")
        redis('DEL','cuentas::'+A)
    save('09-dato-viejo',{'cache_inicial':cached,'transferencia':attempt,'muestras':rows,'saldo_restaurado':get(),'nota':'UPDATE solo en la base aislada del lab; saldo original restaurado al terminar.'})
    assert attempt['status']!=201,attempt
    save('10-estado-final',{'contenedores':dc('ps'),'log_cuenta':logs(),'log_notificacion':dc('logs','--no-color','notificacion')})
else: raise SystemExit('baseline/cache/mongo/viejo')
print('Fase terminada:',phase,flush=True)
