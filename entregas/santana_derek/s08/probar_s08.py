import concurrent.futures
import subprocess
import sys
import time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'recuperacion'))
from http_evidencia import pedir,guardar,ahora

OUT=Path(__file__).resolve().parent/'evidencias'
BASE='http://127.0.0.1:8080/api'
ORIGEN='002180000000000001';DESTINO='002180000000000002'
RUNTIME=str(Path.home()/'.cache/derek-sd-2027-1/s08')
def compose(*args):
    return subprocess.run(['docker','compose','-p','derek-s08','-f','docker-compose.yml','-f','compose.derek.json',*args],cwd=RUNTIME,capture_output=True,text=True,check=True)
def saldos():return {k:pedir(BASE+'/cuentas/'+c) for k,c in [('origen',ORIGEN),('destino',DESTINO)]}

if sys.argv[1]=='problema':
    a='002180000000000007';b='002180000000000008'
    for c,saldo in [(a,1000),(b,500)]:
        alta=pedir(BASE+'/cuentas','POST',{'clabe':c,'titular':'Antes de saga S08','saldoInicial':saldo})
        assert alta['codigo']==201,alta
    result={'antes':{'origen':pedir(BASE+'/cuentas/'+a),'destino':pedir(BASE+'/cuentas/'+b)}}
    with concurrent.futures.ThreadPoolExecutor() as pool:
        future=pool.submit(pedir,BASE+'/transferencias','POST',{'claveOrigen':a,'claveDestino':b,'monto':50},90)
        for _ in range(25):
            saldo=pedir(BASE+'/cuentas/'+a)
            if saldo.get('cuerpo',{}).get('saldo')==950:break
            time.sleep(.5)
        else:raise AssertionError('No se observó el cargo antes de apagar cuenta')
        result['cargo_observado']=saldo;result['apagado']=ahora()
        compose('stop','-t','1','cuenta');result['transferencia']=future.result()
    compose('start','cuenta')
    for _ in range(240):
        primero=pedir(BASE+'/cuentas/'+a,timeout=5)
        if primero['codigo']==200:break
        time.sleep(1)
    else:raise AssertionError('Cuenta no volvió a responder')
    result['despues']={'origen':primero,'destino':pedir(BASE+'/cuentas/'+b)}
    guardar(OUT/'problema-antes.json',result)
    assert result['transferencia']['codigo']>=400 and result['despues']['origen']['cuerpo']['saldo']==950 and result['despues']['destino']['cuerpo']['saldo']==500,result
elif sys.argv[1]=='compensacion':
    for clabe,titular,saldo in [(ORIGEN,'Ana S08',1000),(DESTINO,'Beto S08',500)]:
        alta=pedir(BASE+'/cuentas','POST',{'clabe':clabe,'titular':titular,'saldoInicial':saldo})
        assert alta['codigo']==201,alta
    result={'antes':saldos()}
    result['transferencia']=pedir(BASE+'/transferencias','POST',{'claveOrigen':ORIGEN,'claveDestino':DESTINO,'monto':50})
    result['despues']=saldos()
    result['movimientos']=pedir(BASE+'/movimientos?clabe='+ORIGEN)
    guardar(OUT/'compensacion.json',result)
    assert result['transferencia']['codigo']==409 and result['transferencia']['cuerpo']['estado']=='COMPENSADA',result
    assert result['despues']['origen']['cuerpo']['saldo']==1000 and result['despues']['destino']['cuerpo']['saldo']==500
elif sys.argv[1]=='pendiente':
    import json
    first=json.loads((OUT/'compensacion.json').read_text())['transferencia']['cuerpo']['id']
    siguiente=first+1
    while pedir(BASE+'/transferencias/'+str(siguiente))['codigo']==200:
        siguiente+=1
    result={'inicio':ahora(),'antes':saldos()}
    with concurrent.futures.ThreadPoolExecutor() as pool:
        future=pool.submit(pedir,BASE+'/transferencias','POST',{'claveOrigen':ORIGEN,'claveDestino':DESTINO,'monto':50},90)
        for _ in range(30):
            state=pedir(BASE+'/transferencias/'+str(siguiente))
            if state.get('cuerpo',{}).get('estado')=='CARGADA':break
            time.sleep(.5)
        else: raise AssertionError('No se observó CARGADA antes de la pausa')
        result['cargada']=state
        result['apagado']=ahora();compose('stop','-t','1','cuenta')
        result['transferencia']=future.result()
    guardar(OUT/'compensacion-pendiente.json',result)
    assert result['transferencia']['cuerpo']['estado']=='COMPENSACION_PENDIENTE',result
    muestras=[]
    for _ in range(6):
        muestras.append(pedir(BASE+'/transferencias','POST',{'claveOrigen':ORIGEN,'claveDestino':DESTINO,'monto':1}))
    guardar(OUT/'circuito-abierto.json',muestras)
    compose('start','cuenta')
    print('Cuenta reiniciada. Esperar salud y registro antes de la recuperación.',flush=True)
else:
    result={'saldos':saldos(),'sonda':pedir(BASE+'/transferencias','POST',{'claveOrigen':ORIGEN,'claveDestino':DESTINO,'monto':1})}
    guardar(OUT/'recuperacion.json',result)
    assert result['sonda']['codigo']==201 and result['sonda']['cuerpo']['estado']=='COMPLETADA',result
