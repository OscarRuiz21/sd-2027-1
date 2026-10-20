"""Experimentos S07 reales. Conserva respuestas, horarios y mediciones sin alterar resultados."""
import datetime
import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from zoneinfo import ZoneInfo

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'evidencias'
def now(): return datetime.datetime.now(ZoneInfo('America/Mexico_City')).isoformat()
def req(url,method='GET',data=None):
    start=time.monotonic()
    request=Request(url, data=None if data is None else json.dumps(data).encode(),method=method,
                    headers={'Content-Type':'application/json','Accept':'application/json'})
    try:
        with urlopen(request,timeout=8) as r:
            return {'hora':now(),'codigo':r.status,'cabeceras':dict(r.headers),'cuerpo':r.read().decode(),'segundos':round(time.monotonic()-start,3)}
    except HTTPError as r:
        return {'hora':now(),'codigo':r.code,'cabeceras':dict(r.headers),'cuerpo':r.read().decode(),'segundos':round(time.monotonic()-start,3)}
    except (URLError,TimeoutError,OSError) as e:
        return {'hora':now(),'codigo':0,'error':str(e),'segundos':round(time.monotonic()-start,3)}
def save(name,data):
    (OUT/name).write_text(json.dumps(data,indent=2,ensure_ascii=False))
    print(name,flush=True)

if sys.argv[1]=='antes':
    data={name:req(url,method) for name,url,method in [
        ('cuenta','http://127.0.0.1:8081/cuentas/000','GET'),
        ('movimiento','http://127.0.0.1:8082/movimientos','POST'),
        ('directorio','http://127.0.0.1:8761','GET')]}
    assert data['cuenta']['codigo']==404 and data['movimiento']['codigo']==400 and data['directorio']['codigo']==0,data
    save('antes.json',data)
elif sys.argv[1]=='directorio':
    data=req('http://127.0.0.1:8761/eureka/apps')
    assert data['codigo']==200
    save('directorio-cinco-servicios.json',data)
    with urlopen(Request('http://127.0.0.1:8761/eureka/apps/CUENTA',headers={'Accept':'application/xml'})) as r:
        (OUT/'cuenta-eureka.xml').write_bytes(r.read())
else:
    base='http://127.0.0.1:8080/api'
    clabe='S07'+str(int(time.time()))
    alta=req(base+'/cuentas','POST',{'clabe':clabe,'titular':'S07 Derek','saldoInicial':0})
    assert alta['codigo']==201,alta
    data={'alta':alta,'consulta':req(base+'/cuentas/'+clabe),
          '404-cuenta':req(base+'/cuentas/000'),'404-gateway':req(base+'/movimientos','POST')}
    assert data['consulta']['codigo']==200 and data['404-cuenta']['codigo']==404 and data['404-gateway']['codigo']==404
    data['nueve']=[req(base+'/cuentas/'+clabe) for _ in range(9)]
    instancias={next((v for k,v in x['cabeceras'].items() if k.lower()=='x-instancia'),'') for x in data['nueve']}
    assert len(instancias)==3 and '' not in instancias,instancias
    save('gateway-y-balanceo.json',data)
    experimento={'prediccion':(OUT/'prediccion-caida.txt').read_text(),'muestras':[],'apagado':now()}
    subprocess.run(['docker','kill','derek-s07-cuenta-1'],check=True,capture_output=True)
    inicio=time.monotonic()
    while time.monotonic()-inicio<65:
        muestra=req(base+'/cuentas/'+clabe)
        registro=req('http://127.0.0.1:8761/eureka/apps/CUENTA')
        if registro['codigo']==200:
            n=len(json.loads(registro['cuerpo'])['application']['instance'])
            muestra['replicas-en-registro']=n
            if n==2 and 'salida-del-registro' not in experimento: experimento['salida-del-registro']=now()
        experimento['muestras'].append(muestra)
        time.sleep(1)
    experimento['errores']=sum(x['codigo']!=200 for x in experimento['muestras'])
    save('caida-medida.json',experimento)
    subprocess.run(['docker','start','derek-s07-cuenta-1'],check=True,capture_output=True)
    print('Réplica reiniciada; esperar a UP (3) antes de la demo.',flush=True)
