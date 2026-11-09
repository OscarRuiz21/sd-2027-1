"""Ejecutar por fases en el compose aislado del lab. Guarda respuestas reales."""
import argparse,json,subprocess,time,statistics,math,urllib.request,urllib.error,base64
from pathlib import Path
from datetime import datetime,timezone
ap=argparse.ArgumentParser();ap.add_argument('fase',choices=['base','cache','medidas','mongo','viejo']);ap.add_argument('--directorio',required=True);a=ap.parse_args()
out=Path(__file__).parent/'evidencias';out.mkdir(exist_ok=True)
A='002180000000000001';B='002180000000000002';C='002180000000000003';url='http://127.0.0.1:18080'
inicio_wall=time.time();inicio_mono=time.monotonic()
result={'fase':a.fase,'fecha_utc':datetime.now(timezone.utc).isoformat(),'ejecutor':'Codex, ejecución asistida local','registros':[]}
def record(tipo,data):result['registros'].append({'tipo':tipo,'datos':data});return data
def cmd(*args):
 p=subprocess.run(['docker','compose','-p','derek-s11',*args],cwd=a.directorio,text=True,capture_output=True,timeout=120)
 record('comando',{'args':args,'stdout':p.stdout,'stderr':p.stderr,'codigo':p.returncode});p.check_returncode();return p.stdout.strip()
def redis(*args):return cmd('exec','-T','redis','redis-cli',*args)
def mongo(js):return cmd('exec','-T','mongo','mongosh','notificacion','--quiet','--eval',js)
def http(path,data=None,root=url,headers=None):
 req=urllib.request.Request(root+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json',**(headers or {})})
 start=time.perf_counter()
 try:r=urllib.request.urlopen(req,timeout=35)
 except urllib.error.HTTPError as e:r=e
 body=r.read().decode(); value={'status':r.status,'headers':dict(r.headers),'body':json.loads(body) if body else None,'ms':(time.perf_counter()-start)*1000}
 return record('http',value)
def get():return http('/api/cuentas/'+A)
def transfer(monto=1,dest=B):return http('/api/transferencias',{'claveOrigen':A,'claveDestino':dest,'monto':monto})
def lecturas():return cmd('logs','--no-color','cuenta').count('leida de Postgres')
def stats():
 s=redis('INFO','stats');return {k:int(v) for k,v in (line.split(':',1) for line in s.splitlines() if ':' in line) if k in ('keyspace_hits','keyspace_misses')}
try:
 if a.fase=='base':
  for clabe,titular,saldo in [(A,'Ana',1000),(B,'Beto',500),(C,'Carla',300)]:
   r=http('/api/cuentas',{'clabe':clabe,'titular':titular,'saldoInicial':saldo});assert r['status']==201,r
  before=lecturas()
  for _ in range(10):assert get()['status']==200
  n=lecturas()-before;record('lecturas_postgres',n);assert n==10,n
  stats();assert mongo('db.avisos.countDocuments()')=='0'
 elif a.fase=='cache':
  redis('DEL','cuentas::'+A);before=lecturas();rs=[get() for _ in range(6)]
  assert all(r['status']==200 for r in rs)
  n=lecturas()-before;record('lecturas_postgres',n);assert n==1,n
  instancias=set(r['headers'].get('X-Instancia',r['headers'].get('x-instancia')) for r in rs)
  record('instancias',sorted(str(x) for x in instancias));assert len(instancias)==3 and None not in instancias,instancias
  assert len(set(r['body']['leidaEn'] for r in rs))==1
  redis('GET','cuentas::'+A);redis('TTL','cuentas::'+A)
  assert transfer()['status']==201
  assert redis('EXISTS','cuentas::'+A)=='0'
  get()
 elif a.fase=='medidas':
  for escenario in ([] if __import__('os').environ.get('MEDICION_CACHE')=='none' else ['A','B']):
   redis('DEL','cuentas::'+A);redis('CONFIG','RESETSTAT');before=lecturas()
   for i in range(100):
    assert get()['status']==200
    if escenario=='B' and (i+1)%10==0:assert transfer()['status']==201
   s=stats();s.update(escenario=escenario,ratio=s['keyspace_hits']/(s['keyspace_hits']+s['keyspace_misses']),lecturas_postgres=lecturas()-before);record('hit_ratio',s)
  # La variable CACHE_TIPO se cambia fuera de este script; dos fases de latencia separadas.
  import os
  for corrida in range(1,4):
   before=lecturas();rs=[get() for _ in range(100)];assert all(r['status']==200 for r in rs)
   ts=sorted(r['ms'] for r in rs)
   record('latencia',{'tipo':os.environ.get('MEDICION_CACHE','redis'),'corrida':corrida,'n':100,'promedio_ms':statistics.mean(ts),'mediana_ms':statistics.median(ts),'p95_ms':ts[math.ceil(.95*len(ts))-1],'lecturas_postgres':lecturas()-before})
 elif a.fase=='mongo':
  assert transfer(dest=C)['status']==201
  for _ in range(12):assert transfer()['status']==201
  time.sleep(4)
  mongo('EJSON.stringify(db.avisos.find().sort({fecha:1}).toArray())')
  mongo('EJSON.stringify(db.avisos.find({telefono:{$exists:true}},{clabe:1,canal:1,telefono:1}).toArray())')
  r=http('/api/notificaciones/'+B);assert r['status']==200 and len(r['body'])==10,r
  mongo('EJSON.stringify(db.avisos.getIndexes())')
  q='db.avisos.find({clabe:"'+B+'"}).sort({fecha:-1}).limit(10)'
  mongo('EJSON.stringify('+q+'.explain("executionStats"))');mongo('EJSON.stringify('+q+'.hint({$natural:1}).explain("executionStats"))')
  evento={'eventoId':'11111111-2222-4333-8444-555555555555','tipo':'TransferenciaRealizada','transferenciaId':9001,'clabeOrigen':'002180000000000091','clabeDestino':'002180000000000092','monto':25,'saldoOrigen':475,'saldoDestino':525,'fecha':datetime.now(timezone.utc).isoformat()}
  for _ in range(2):
   r=http('/api/exchanges/%2F/transferencias/publish',{'properties':{'delivery_mode':2,'content_type':'application/json'},'routing_key':'transferencia.realizada','payload_encoding':'string','payload':json.dumps(evento)},root='http://127.0.0.1:15674',headers={'Authorization':'Basic '+base64.b64encode(b'mexibanco:mexibanco').decode()});assert r['body']['routed']
  time.sleep(3)
  assert mongo('db.avisos.countDocuments({eventoId:"'+evento['eventoId']+'"})')=='2'
  cmd('logs','--no-color','--tail','60','notificacion')
 elif a.fase=='viejo':
  redis('DEL','cuentas::'+A);original=get()['body']['saldo'];t=time.monotonic()
  sql=lambda s:cmd('exec','-T','db','psql','-U','mexibanco','-d','cuenta','-c',s)
  try:
   sql("update cuenta set saldo=5 where clabe='"+A+"'")
   r=transfer(100);record('rechazo_saldo_real',r);assert r['status'] in (400,409,422),r
   for i in range(9):
    record('dato_viejo',{'segundos':time.monotonic()-t,'ttl':redis('TTL','cuentas::'+A),'respuesta':get()})
    if i<8:time.sleep(5)
  finally:
   sql("update cuenta set saldo="+str(original)+" where clabe='"+A+"'");redis('DEL','cuentas::'+A)
 result['resultado']='OK'
finally:
 result['fin_utc']=datetime.now(timezone.utc).isoformat()
 result['duracion_wall_s']=time.time()-inicio_wall
 result['duracion_monotona_s']=time.monotonic()-inicio_mono
 result['posible_suspension']=abs(result['duracion_wall_s']-result['duracion_monotona_s'])>5
 suffix='-'+__import__('os').environ.get('MEDICION_CACHE','redis') if a.fase=='medidas' else ''
 (out/(a.fase+suffix+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2))
 print(a.fase,result.get('resultado','FALLO: revisar evidencia'))
