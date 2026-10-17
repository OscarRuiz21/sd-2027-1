"""Obtiene respuestas HTTP, SQL y logs reales; conserva salidas sin modificarlas."""
from pathlib import Path
import json, subprocess, urllib.request, urllib.error, time, concurrent.futures, os, sys
ROOT=Path(__file__).resolve().parent
OUT=ROOT/'evidencias-reales'; OUT.mkdir(exist_ok=True)
BASE=['docker','compose','-f',str(ROOT/'docker-compose.yml'),'-f',str(ROOT/'compose-evidencias.yml')]
API='http://localhost:18080/api'
def cmd(args):
 p=subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace',timeout=120)
 return '$ '+' '.join(args)+'\n'+p.stdout+p.stderr+'\nexit='+str(p.returncode)+'\n'
def request(path,body=None):
 data=json.dumps(body).encode() if body else None
 r=urllib.request.Request(API+path,data=data,headers={'Content-Type':'application/json'})
 start=time.monotonic()
 try:
  with urllib.request.urlopen(r,timeout=60) as response: status=response.status; raw=response.read().decode()
 except urllib.error.HTTPError as e: status=e.code; raw=e.read().decode()
 try: value=json.loads(raw)
 except ValueError: value=raw
 return {'url':API+path,'method':'POST' if body else 'GET','body':body,'status':status,'seconds':round(time.monotonic()-start,3),'response':value}
def save(name,items):
 text=json.dumps(items,ensure_ascii=False,indent=2)
 (OUT/(name+'.json')).write_text(text,encoding='utf-8'); print(name, text,flush=True)
def balances(o,d): return [request('/cuentas/'+o),request('/cuentas/'+d)]
def ready():
 for i in range(60):
  try:
   if request('/cuentas/000')['status']==404 and request('/transferencias/999999')['status']==404:return
  except Exception:pass
  time.sleep(2)
 raise RuntimeError('Gateway no listo')
def accounts(o,d):
 save('alta-'+o,[request('/cuentas',{'clabe':o,'titular':'Ana S08','saldoInicial':1000}),request('/cuentas',{'clabe':d,'titular':'Beto S08','saldoInicial':500})])
def transfer(o,d):return request('/transferencias',{'claveOrigen':o,'claveDestino':d,'monto':200})
mode=sys.argv[1]
ready()
if mode=='original':
 o,d='002187000000001081','002187000000001082';accounts(o,d)
 save('01-original-antes',balances(o,d))
 (OUT/'00-contenedores.txt').write_text(cmd(BASE+['ps']),encoding='utf-8')
 with concurrent.futures.ThreadPoolExecutor() as pool:
  future=pool.submit(transfer,o,d);time.sleep(5)
  (OUT/'02-original-apagar.txt').write_text(cmd(BASE+['stop','cuenta']),encoding='utf-8')
  save('03-original-respuesta',future.result())
 (OUT/'04-original-encender.txt').write_text(cmd(BASE+['start','cuenta']),encoding='utf-8')
 ready();save('05-original-despues',balances(o,d))
 assert balances(o,d)[0]['response']['saldo']==800
elif mode=='compensada':
 o,d='002187000000004081','002187000000004082';accounts(o,d)
 save('06-saga-antes',balances(o,d));result=transfer(o,d);save('07-saga-respuesta',result)
 save('08-saga-despues',balances(o,d))
 assert result['response']['estado']=='COMPENSADA',result
 assert balances(o,d)[0]['response']['saldo']==1000
 (OUT/'09-saga-sql.txt').write_text(cmd(BASE+['exec','-T','db','psql','-U','mexibanco','-d','transferencia','-c','select id,clave_origen,clave_destino,monto,estado,detalle from transferencia order by id;']),encoding='utf-8')
 (OUT/'10-saga-movimientos.txt').write_text(cmd(BASE+['exec','-T','db','psql','-U','mexibanco','-d','movimiento','-c','select * from movimiento order by id;']),encoding='utf-8')
elif mode=='pendiente':
 o,d='002187000000003081','002187000000003082';accounts(o,d)
 save('11-pendiente-antes',balances(o,d))
 with concurrent.futures.ThreadPoolExecutor() as pool:
  future=pool.submit(transfer,o,d);time.sleep(5)
  save('12-pendiente-durante',balances(o,d))
  (OUT/'13-pendiente-apagar.txt').write_text(cmd(BASE+['stop','cuenta']),encoding='utf-8')
  result=future.result();save('14-pendiente-respuesta',result)
 assert result['response']['estado']=='COMPENSACION_PENDIENTE',result
 (OUT/'15-pendiente-sql.txt').write_text(cmd(BASE+['exec','-T','db','psql','-U','mexibanco','-d','transferencia','-c','select id,estado,detalle from transferencia order by id;']),encoding='utf-8')
 save('16-circuito-caido',[transfer(o,d) for _ in range(5)])
 (OUT/'17-circuito-abierto.txt').write_text(cmd(BASE+['logs','--no-color','transferencia']),encoding='utf-8')
 (OUT/'18-encender.txt').write_text(cmd(BASE+['start','cuenta']),encoding='utf-8')
 ready();time.sleep(12)
 for i in range(15):
  result=transfer(o,o)
  # Validacion local no sondea cuenta: usar una CLABE inexistente como destino.
  result=transfer(o,'002187000000009999')
  if result['status']==404:break
  time.sleep(3)
 save('19-circuito-recuperado',result)
 save('20-pendiente-despues',balances(o,d))
 (OUT/'21-circuito-estados.txt').write_text(cmd(BASE+['logs','--no-color','transferencia']),encoding='utf-8')
