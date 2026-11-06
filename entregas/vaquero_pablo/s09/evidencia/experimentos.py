from pathlib import Path
import json, os, sys, time, subprocess, uuid, base64, urllib.request, urllib.error
from datetime import datetime,timezone
EV=Path(__file__).resolve().parent
APP=Path(os.environ.get('S09_APP',str(EV.parents[4]/'mexi-banco-s09')))
BASE='http://localhost:18082'
ENV=dict(os.environ,PUERTO_GATEWAY='18082')
A,B='002180000000000001','002180000000000002'
def cmd(*args):
    return subprocess.run(['docker','compose','-p','mexibanco-s09-pablo','-f',str(APP/'docker-compose.yml'),*args],
        text=True,capture_output=True,check=True,env=ENV).stdout
def http(path,body=None,origin=BASE,auth=False):
    headers={'Content-Type':'application/json'}
    if auth: headers['Authorization']='Basic '+base64.b64encode(b'mexibanco:mexibanco').decode()
    req=urllib.request.Request(origin+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try: r=urllib.request.urlopen(req,timeout=40)
    except urllib.error.HTTPError as e:r=e
    raw=r.read().decode()
    try: data=json.loads(raw)
    except ValueError:data=raw
    return dict(status=r.code,body=data)
def sql(query,db='notificacion'):
    return cmd('exec','-T','db','psql','-X','-t','-A','-U','mexibanco','-d',db,'-c',query).strip()
def transfer(monto=1):
    # Esperar una lectura inocua antes de enviar una mutacion. No reintentar el POST a ciegas.
    for _ in range(60):
        try:
            if http('/api/transferencias/1')['status'] in (200,404):break
        except Exception:pass
        time.sleep(.5)
    else:raise RuntimeError('Transferencia aun no registrada en el gateway')
    return http('/api/transferencias',dict(claveOrigen=A,claveDestino=B,monto=monto))
def queue(name='notificacion.transferencias'):
    return http('/api/queues/%2F/'+name,origin='http://localhost:15672',auth=True)
def publish(payload,ruta='transferencia.realizada'):
    return http('/api/exchanges/%2F/transferencias/publish',dict(properties={'delivery_mode':2},routing_key=ruta,
        payload=json.dumps(payload) if not isinstance(payload,str) else payload,payload_encoding='string'),
        origin='http://localhost:15672',auth=True)
def evento(monto):return dict(eventoId=str(uuid.uuid4()),tipo='TransferenciaRealizada',transferenciaId=99999,
    clabeOrigen=A,clabeDestino=B,monto=monto,saldoOrigen=123.45,saldoDestino=678.90,fecha=datetime.now(timezone.utc).isoformat())
def save(name,**data):
    data=dict(fecha=datetime.now(timezone.utc).isoformat(),**data)
    (EV/(name+'.json')).write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
    print(name,json.dumps({k:v for k,v in data.items() if k!='logs'},ensure_ascii=False),flush=True)
def wait():
    for _ in range(100):
        try:
            if http('/api/cuentas/'+A)['status'] in (200,404):return
        except Exception:pass
        time.sleep(2)
    raise RuntimeError('Servicios no listos')
def until(check,expected):
    for _ in range(60):
        result=check()
        if result==expected:return result
        time.sleep(.5)
    raise AssertionError((result,expected))
def count(monto):return int(sql("SELECT count(*) FROM notificacion WHERE mensaje LIKE '% por "+str(monto)+",%'") or 0)
def bandeja(clabe):
    for _ in range(60):
        r=http('/api/notificaciones?clabe='+clabe)
        if r['status']==200:return r
        time.sleep(.5)
    raise AssertionError(r)
def base():
    wait()
    for clabe,titular,saldo in [(A,'Ana',1000),(B,'Beto',500)]:
        r=http('/api/cuentas',dict(clabe=clabe,titular=titular,saldoInicial=saldo));assert r['status']==201,r
    cmd('stop','notificacion')
    try:
        r=transfer(10);state=http('/api/transferencias/1');saldos=[http('/api/cuentas/'+c) for c in (A,B)]
        assert r['status']>=400 and state['body']['estado']=='ABONADA',(r,state)
        save('01-problema-base',transferencia=r,estado=state,saldos=saldos)
    finally:cmd('start','notificacion')
def desacople():
    wait();cmd('stop','notificacion')
    try:
        ts=[transfer(1) for _ in range(3)];assert all(t['status']==201 for t in ts),ts
        until(lambda:queue()['body'].get('messages_ready'),3)
        save('02-tres-ready',transferencias=ts,cola=queue())
        time.sleep(int(os.environ.get('S09_PAUSA_CAPTURA','0')))
    finally:cmd('start','notificacion')
    until(lambda:queue()['body'].get('messages'),0)
    save('03-consumidos',cola=queue(),bandejas=[bandeja(c) for c in (A,B)])
def duplicado(ingenuo):
    monto=23.45 if ingenuo else 27.89
    e=evento(monto);antes=count(monto);publicaciones=[];conteos=[]
    for i in (1,2):
        publicaciones.append(publish(e))
        esperado=antes+(2*i if ingenuo else 2)
        until(lambda:count(monto),esperado);time.sleep(.5);conteos.append(count(monto)-antes)
    assert conteos==([2,4] if ingenuo else [2,2]),conteos
    save('04-duplicado-ingenuo' if ingenuo else '05-duplicado-resuelto',evento=e,publicaciones=publicaciones,
        avisos_nuevos=conteos,bandeja=bandeja(A),logs=cmd('logs','--tail','30','notificacion'))
def poison(antes):
    if antes:
        r=publish('{"hola": ')
        until(lambda:queue()['body'].get('messages',0)>=1,True)
        q=queue(); dlq=queue('notificacion.transferencias.dlq')
        assert q['body']['messages']>=1 and dlq['body']['messages']==0,(q,dlq)
        save('06-veneno-antes',publicacion=r,cola=q,dlq=dlq,logs=cmd('logs','--tail','35','notificacion'))
    else:
        until(lambda:queue('notificacion.transferencias.dlq')['body'].get('messages'),1)
        normal=transfer(2);assert normal['status']==201,normal
        save('07-veneno-dlq',cola=queue(),dlq=queue('notificacion.transferencias.dlq'),normal=normal,
            logs=cmd('logs','--tail','35','notificacion'))
def compensada():
    r=transfer(5)
    assert r['body']['estado']=='COMPENSADA',r
    time.sleep(2)
    save('08-compensada',transferencia=r,bandeja=bandeja(A))
def brokercaido():
    cmd('stop','rabbitmq')
    try:
        r=transfer(3)
        ultimo=sql('SELECT id FROM transferencia ORDER BY id DESC LIMIT 1','transferencia')
        estado=http('/api/transferencias/'+ultimo)
        assert estado['body']['estado']=='COMPLETADA',estado
        save('09-broker-caido',respuesta=r,estado=estado,saldos=[http('/api/cuentas/'+c) for c in (A,B)])
    finally:cmd('start','rabbitmq')
if __name__=='__main__':
    etapa=sys.argv[1]
    if etapa=='base':base()
    elif etapa=='desacople':desacople()
    elif etapa in ('ingenuo','resuelto'):duplicado(etapa=='ingenuo')
    elif etapa in ('veneno-antes','veneno-despues'):poison(etapa=='veneno-antes')
    elif etapa=='compensada':compensada()
    elif etapa=='broker':brokercaido()
    else:raise SystemExit('Etapa desconocida')
