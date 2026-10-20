"""Pruebas reales del lab sobre el proyecto Docker aislado derek-s09."""
import base64
import hashlib
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'recuperacion'))
from http_evidencia import pedir,guardar,ahora

OUT=Path(__file__).resolve().parent/'evidencias'
BASE='http://127.0.0.1:8080/api';BROKER='http://127.0.0.1:15672/api'
A='002180000000000001';B='002180000000000002'
AUTH={'Authorization':'Basic '+base64.b64encode(b'mexibanco:mexibanco').decode()}
def rabbit(path,method='GET',data=None):return pedir(BROKER+path,method,data,headers=AUTH)
def cola(nombre='notificacion.transferencias'):return rabbit('/queues/%2F/'+nombre)
def bandeja(c):return pedir(BASE+'/notificaciones?clabe='+c)
def saldos(a=A,b=B):return {'origen':pedir(BASE+'/cuentas/'+a),'destino':pedir(BASE+'/cuentas/'+b)}
def transferir(a=A,b=B):return pedir(BASE+'/transferencias','POST',{'claveOrigen':a,'claveDestino':b,'monto':1})
def crear(a,b):
    for c in [a,b]:
        result=pedir(BASE+'/cuentas','POST',{'clabe':c,'titular':'Prueba S09','saldoInicial':100})
        assert result['codigo']==201,result
def esperar(fn,predicate,segundos=45):
    start=time.monotonic()
    while time.monotonic()-start<segundos:
        result=fn()
        if predicate(result):return result
        time.sleep(1)
    raise AssertionError(result)
def publicar(payload):
    result=rabbit('/exchanges/%2F/transferencias/publish','POST',{
        'routing_key':'transferencia.realizada','properties':{'content_type':'application/json','delivery_mode':2},
        'payload':payload if isinstance(payload,str) else json.dumps(payload),'payload_encoding':'string'})
    assert result['codigo']==200 and result['cuerpo']['routed'],result
    return result
def evento(t):
    source=t['cuerpo'];key='transferencia-'+str(source['id'])+'-TransferenciaRealizada'
    ident=str(uuid.UUID(bytes=hashlib.md5(key.encode()).digest(),version=3))
    return {'eventoId':ident,'tipo':'TransferenciaRealizada','transferenciaId':source['id'],
        'clabeOrigen':source['claveOrigen'],'clabeDestino':source['claveDestino'],'monto':source['monto'],
        'saldoOrigen':source['saldoOrigenTrasCargo'],
        'saldoDestino':pedir(BASE+'/cuentas/'+source['claveDestino'])['cuerpo']['saldo'],'fecha':source['actualizada']}

phase=sys.argv[1]
if phase=='problema':
    for c,name,saldo in [(A,'Ana S09',1000),(B,'Beto S09',500)]:
        r=pedir(BASE+'/cuentas','POST',{'clabe':c,'titular':name,'saldoInicial':saldo});assert r['codigo']==201,r
    result={'antes':saldos()}
    subprocess.run(['docker','stop','-t','1','derek-s09-notificacion-1'],capture_output=True,check=True)
    result['transferencia']=transferir()
    result['estado']=pedir(BASE+'/transferencias/1');result['despues']=saldos()
    guardar(OUT/'problema.json',result)
    assert result['transferencia']['codigo']>=400 and result['estado']['cuerpo']['estado']=='ABONADA',result
elif phase=='publicar':
    result={'transferencia':transferir(),'exchange':rabbit('/exchanges/%2F/transferencias'),'cola':cola()}
    guardar(OUT/'publicacion.json',result)
    assert result['transferencia']['codigo']==201 and result['transferencia']['cuerpo']['estado']=='COMPLETADA',result
    assert result['exchange']['codigo']==200 and result['cola']['codigo']==404,result
elif phase=='cola':
    assert cola()['cuerpo'].get('messages',0)==0
    subprocess.run(['docker','stop','-t','1','derek-s09-notificacion-1'],capture_output=True,check=True)
    transfers=[transferir() for _ in range(3)]
    assert all(t['codigo']==201 for t in transfers),transfers
    estado=esperar(cola,lambda r:r['cuerpo'].get('messages_ready')==3)
    guardar(OUT/'tres-ready.json',{'transferencias':transfers,'cola':estado})
elif phase=='consumir':
    q=esperar(cola,lambda r:r['cuerpo'].get('messages')==0)
    a=esperar(lambda:bandeja(A),lambda r:r['codigo']==200 and len(r['cuerpo'])==3)
    b=bandeja(B);assert b['codigo']==200 and len(b['cuerpo'])==3,b
    guardar(OUT/'bandeja.json',{'cola':q,'origen':a,'destino':b})
elif phase in ('duplicado-antes','duplicado-despues'):
    before=phase.endswith('antes')
    a,b=('002180000000000003','002180000000000004') if before else ('002180000000000005','002180000000000006')
    crear(a,b);t=transferir(a,b);assert t['codigo']==201,t
    primera=esperar(lambda:bandeja(a),lambda r:r['codigo']==200 and len(r['cuerpo'])==1)
    e=evento(t);pub=publicar(e)
    if before:final=esperar(lambda:bandeja(a),lambda r:len(r.get('cuerpo',[]))==2)
    else:
        publicar(e);publicar(e)
        esperar(cola,lambda r:r['cuerpo'].get('messages')==0)
        final=bandeja(a);assert len(final['cuerpo'])==1,final
    result={'transferencia':t,'evento_republicado':e,'primera':primera,'publicacion':pub,'final_origen':final,'final_destino':bandeja(b)}
    guardar(OUT/(phase+'.json'),result)
elif phase=='veneno-antes':
    result={'publicacion':publicar('{"hola":')}
    time.sleep(2);result['cola']=cola()
    subprocess.run(['docker','stop','-t','1','derek-s09-notificacion-1'],capture_output=True,check=True)
    result['logs']=subprocess.check_output(['docker','logs','--tail','25','derek-s09-notificacion-1'],stderr=subprocess.STDOUT).decode()
    # Solo se retira el mensaje de prueba conocido; no se vacía ni se purga la cola.
    bad=rabbit('/queues/%2F/notificacion.transferencias/get','POST',{'count':1,'ackmode':'ack_requeue_false','encoding':'auto'})
    assert bad['codigo']==200 and len(bad['cuerpo'])==1 and bad['cuerpo'][0]['payload']=='{"hola":',bad
    result['mensaje_retirado']=bad
    esperar(cola,lambda r:r['cuerpo'].get('messages')==0)
    result['eliminacion_cola_vacia']=rabbit('/queues/%2F/notificacion.transferencias?if-unused=true&if-empty=true','DELETE')
    assert result['eliminacion_cola_vacia']['codigo']==204,result
    guardar(OUT/'veneno-antes.json',result)
elif phase=='dlq':
    result={'publicacion':publicar('{"hola":')}
    result['dlq']=esperar(lambda:cola('notificacion.transferencias.dlq'),lambda r:r['cuerpo'].get('messages_ready')==1)
    result['mensaje']=rabbit('/queues/%2F/notificacion.transferencias.dlq/get','POST',{'count':1,'ackmode':'ack_requeue_true','encoding':'auto'})
    death=result['mensaje']['cuerpo'][0]['properties']['headers']['x-death'][0]
    assert death['reason']=='rejected',death
    previous=len(bandeja(A)['cuerpo']);result['transferencia_normal']=transferir()
    assert result['transferencia_normal']['codigo']==201
    result['bandeja_normal']=esperar(lambda:bandeja(A),lambda r:r['codigo']==200 and len(r['cuerpo'])==previous+1)
    guardar(OUT/'dlq.json',result)
else:
    result={'antes':saldos(),'transferencia':transferir(),'despues':saldos()}
    assert result['transferencia']['cuerpo']['estado']=='COMPENSADA',result
    assert all(result['antes'][k]['cuerpo']['saldo']==result['despues'][k]['cuerpo']['saldo'] for k in ['origen','destino']),result
    result['aviso']=esperar(lambda:bandeja(A),lambda r:r['codigo']==200 and any('devolvimos' in n['mensaje'] for n in r['cuerpo']))
    guardar(OUT/'revertida.json',result)
