"""Demostración controlada de un exchange sin ruta hacia la cola. No borra colas ni mensajes."""
import runpy,urllib.request,urllib.parse,json,time
from pathlib import Path
x=runpy.run_path(str(Path(__file__).with_name('experimentos.py')),run_name='referencia')
h=x['http'];cmd=x['cmd'];base='http://localhost:15672'
cmd('stop','notificacion')
try:
    bindings=h('/api/bindings/%2F/e/transferencias/q/notificacion.transferencias',origin=base,auth=True)['body']
    for b in bindings:
        req=urllib.request.Request(base+'/api/bindings/%2F/e/transferencias/q/notificacion.transferencias/'+urllib.parse.quote(b['properties_key'],safe=''),method='DELETE',headers={'Authorization':'Basic bWV4aWJhbmNvOm1leGliYW5jbw=='})
        urllib.request.urlopen(req).read()
    r=x['transfer'](1)
    ultimo=x['sql']('SELECT max(id) FROM transferencia','transferencia')
    estado=h('/api/transferencias/'+ultimo)
    assert r['status']>=400 and estado['body']['estado']=='COMPLETADA',(r,estado)
    x['save']('10-sin-binding',respuesta=r,estado=estado,logs=cmd('logs','--tail','60','transferencia'))
finally:
    h('/api/bindings/%2F/e/transferencias/q/notificacion.transferencias',dict(routing_key='transferencia.*',arguments={}),origin=base,auth=True)
    cmd('start','notificacion')
