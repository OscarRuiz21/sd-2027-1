"""Experimentos reales; ejecutar con --fase base, cola, antes, despues o revertida.
No borra datos. Requiere el proyecto Compose mexi-banco-s09 en ejecucion.
"""
import argparse, json, os, subprocess, time, uuid
from pathlib import Path
import requests

ROOT=Path(os.environ.get('MEXIBANCO_PROYECTO', str(Path(__file__).resolve().parent))).resolve()
E=ROOT/'evidencias'; E.mkdir(exist_ok=True)
A='002180000000000001'; B='002180000000000002'
API='http://localhost:18090/api'; R='http://localhost:15672/api'
AUTH=('mexibanco','mexibanco')
DOCKER=['docker','--context','desktop-linux','compose','-p','mexi-banco-s09','-f',str(ROOT/'docker-compose.yml')]
def dc(*args):
    p=subprocess.run(DOCKER+list(args),capture_output=True,text=True,encoding='utf-8',errors='replace')
    if p.returncode: raise RuntimeError(p.stdout+p.stderr)
    return p.stdout+p.stderr
def guardar(nombre, dato):
    texto=dato if isinstance(dato,str) else json.dumps(dato,ensure_ascii=False,indent=2)
    (E/(nombre+'.txt')).write_text(texto,encoding='utf-8')
    print(nombre, texto[:300],flush=True)
def pedir(method,path,body=None):
    r=requests.request(method,API+path,json=body,timeout=45)
    try: data=r.json()
    except ValueError: data=r.text
    return {'http':r.status_code,'respuesta':data}
def transferir(monto): return pedir('POST','/transferencias',{'claveOrigen':A,'claveDestino':B,'monto':monto})
def saldos(): return [pedir('GET','/cuentas/'+c) for c in [A,B]]
def bandejas(): return {c:pedir('GET','/notificaciones?clabe='+c) for c in [A,B]}
def cola(nombre='notificacion.transferencias'):
    r=requests.get(R+'/queues/%2F/'+nombre,auth=AUTH,timeout=15); r.raise_for_status()
    q=r.json()
    # Management statistics are sampled, use rabbitmqctl for exact counts as well.
    return {k:q.get(k) for k in ['name','durable','arguments','messages','messages_ready','messages_unacknowledged','consumers']}
def publicar(payload,key='transferencia.realizada'):
    r=requests.post(R+'/exchanges/%2F/transferencias/publish',auth=AUTH,json={
        'properties':{'delivery_mode':2,'content_type':'application/json'},'routing_key':key,
        'payload':payload if isinstance(payload,str) else json.dumps(payload),'payload_encoding':'string'},timeout=15)
    r.raise_for_status(); assert r.json()['routed']; return r.json()
def exactas(): return dc('exec','-T','rabbitmq','rabbitmqctl','list_queues','name','messages_ready','messages_unacknowledged')
def esperar_vacia():
    for _ in range(45):
        salida=exactas()
        if 'notificacion.transferencias\t0\t0' in salida: return
        time.sleep(1)
    raise AssertionError('La cola no se vacio')
def capturar(nombre):
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True,channel='msedge',args=['--disable-gpu','--disable-software-rasterizer'],timeout=20000)
        ctx=browser.new_context(viewport={'width':1440,'height':1050},http_credentials={'username':AUTH[0],'password':AUTH[1]})
        page=ctx.new_page(); page.set_default_timeout(20000)
        page.goto('http://localhost:15672/#/queues/%2F/notificacion.transferencias',wait_until='domcontentloaded')
        if page.locator('input[name=username]').count():
            page.locator('input[name=username]').fill(AUTH[0]); page.locator('input[name=password]').fill(AUTH[1])
            page.locator('input[type=submit]').click(); page.wait_for_timeout(2000)
            page.goto('http://localhost:15672/#/queues/%2F/notificacion.transferencias',wait_until='domcontentloaded')
        page.wait_for_timeout(2500); page.screenshot(path=str(E/(nombre+'.png')),full_page=True)
        page.goto('http://localhost:15672/#/queues/%2F/notificacion.transferencias.dlq',wait_until='domcontentloaded')
        page.wait_for_timeout(2000); page.screenshot(path=str(E/(nombre+'-dlq.png')),full_page=True)
        browser.close()

def run(fase):
    for _ in range(90):
        try:
            if pedir('GET','/cuentas/000')['http']==404 and pedir('GET','/transferencias/999999')['http']==404: break
        except requests.RequestException: pass
        time.sleep(3)
    else: raise RuntimeError('Servicios no disponibles por el gateway')
    if fase=='base':
        for c,n,s in [(A,'Ana',1000),(B,'Beto',500)]:
            guardar('00-alta-'+n,pedir('POST','/cuentas',{'clabe':c,'titular':n,'saldoInicial':s}))
        guardar('01-saldos-antes',saldos()); dc('stop','notificacion')
        guardar('01-problema-http',transferir(50)); guardar('01-problema-saldos',saldos())
        guardar('01-problema-abonada',pedir('GET','/transferencias?estado=ABONADA'))
        guardar('01-problema-logs',dc('logs','--tail','80','transferencia')); dc('start','notificacion')
    elif fase=='cola':
        dc('stop','notificacion')
        resultados=[transferir(n) for n in [11,12,13]]
        assert all(r['http']==201 and r['respuesta']['estado']=='COMPLETADA' for r in resultados),resultados
        guardar('02-tres-transferencias',resultados)
        time.sleep(6); guardar('02-cola-ready',cola()); guardar('02-cola-exacta',exactas())
        assert 'notificacion.transferencias\t3\t0' in exactas()
        capturar('02-panel-ready'); dc('start','notificacion'); esperar_vacia(); time.sleep(3)
        guardar('02-bandejas',bandejas()); guardar('02-cola-vacia',exactas())
        guardar('02-consumidor-logs',dc('logs','--tail','45','notificacion')); capturar('02-panel-vacia')
    elif fase in ['antes','despues']:
        evento={'eventoId':str(uuid.uuid4()),'tipo':'TransferenciaRealizada','transferenciaId':9001 if fase=='antes' else 9002,
            'clabeOrigen':A,'clabeDestino':B,'monto':21 if fase=='antes' else 22,
            'saldoOrigen':914,'saldoDestino':586,'fecha':'2026-10-22T12:00:00Z'}
        previo=bandejas(); publicar(evento); publicar(evento); esperar_vacia(); time.sleep(2); luego=bandejas()
        diferencia={c:len(luego[c]['respuesta'])-len(previo[c]['respuesta']) for c in [A,B]}
        assert all(n==(2 if fase=='antes' else 1) for n in diferencia.values()),diferencia
        guardar('03-duplicado-'+fase,{'evento':evento,'avisos_nuevos_por_cuenta':diferencia,'bandejas':luego})
        publicar('{"hola": '); time.sleep(4)
        guardar('04-veneno-'+fase,{'cola':cola(),'dlq':cola('notificacion.transferencias.dlq')})
        guardar('04-logs-'+fase,dc('logs','--tail','65','notificacion')); capturar('04-panel-'+fase)
        if fase=='despues':
            assert cola('notificacion.transferencias.dlq')['messages_ready']>=1
            r=transferir(1); assert r['http']==201; esperar_vacia(); guardar('04-normal-tras-veneno',{'transferencia':r,'bandejas':bandejas()})
    elif fase=='revertida':
        antes=saldos(); r=transferir(150); despues=saldos()
        assert r['http']==502,r
        assert r['respuesta']['estado']=='COMPENSADA',r
        assert antes==despues,(antes,despues)
        esperar_vacia()
        for _ in range(30):
            avisos=bandejas()
            if any('DEVOLUCION_TRANSFERENCIA por 150' in a['mensaje'] for a in avisos[A]['respuesta']): break
            time.sleep(1)
        else: raise AssertionError('Falta aviso de devolucion')
        guardar('05-revertida',{'respuesta':r,'saldos_antes':antes,'saldos_despues':despues,'bandejas':avisos})
    guardar('estado-contenedores',dc('ps'))

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--fase',required=True)
    run(parser.parse_args().fase)
