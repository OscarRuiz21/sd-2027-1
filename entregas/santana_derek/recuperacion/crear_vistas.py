"""Vistas de resultados originales para las capturas de las bitácoras."""
import html
import json
import re
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def esc(value):return html.escape(str(value))
def body(response):
    value=response.get('cuerpo',{})
    if isinstance(value,str):
        try:return json.loads(value)
        except ValueError:return value
    return value
def read(folder,name):return json.loads((folder/name).read_text())
def table(head,rows):
    return '<table><thead><tr>'+''.join('<th>'+esc(c)+'</th>' for c in head)+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+esc(c)+'</td>' for c in row)+'</tr>' for row in rows)+'</tbody></table>'
def dump(value):return '<pre>'+esc(json.dumps(value,ensure_ascii=False,indent=2))+'</pre>'
def view(folder,name,title,content,sources):
    css='body{font:15px/1.3 system-ui;color:#14314a;background:#f5f8fa;margin:22px;max-width:1180px}h1{font-size:27px;margin:0 0 5px}h2{font-size:19px;color:#087e8b;margin:17px 0 8px}small{color:#50677b}table{width:100%;border-collapse:collapse;background:#fff;margin:12px 0}th,td{text-align:left;padding:6px 10px;border:1px solid #cbd7df}th{background:#e4f0f2}pre{background:#fff;border:1px solid #cbd7df;padding:12px;font:13px/1.4 monospace;white-space:pre-wrap;overflow-wrap:anywhere}a{color:#087e8b}'
    links=' · '.join('<a href="'+esc(n)+'">'+esc(n)+'</a>' for n in sources)
    (folder/name).write_text('<!doctype html><html lang="es"><meta charset="utf-8"><title>'+esc(title)+'</title><style>'+css+'</style><h1>'+esc(title)+'</h1><small>Registros de ejecución asistida. Se conservan las fechas y zonas horarias originales. Esta vista no es una captura de Terminal.</small>'+content+'<p><small>Fuentes completas: '+links+'</small></p></html>')
def saldos(data):
    return [(name,body(data[name]).get('saldo','sin respuesta')) for name in ['origen','destino']]
def respuesta(r):
    value=body(r)
    return [r.get('codigo'),value.get('estado','') if isinstance(value,dict) else '',r.get('hora','')]

semana=sys.argv[1];folder=ROOT/semana/'evidencias'
if semana=='s07':
    if (folder/'antes.json').exists():
        a=read(folder,'antes.json');rows=[]
        for label,key in [('GET de cuenta inexistente','cuenta'),('POST directo a movimientos','movimiento'),('GET de salud del directorio','directorio')]:
            r=a[key];value=body(r);detail=value.get('detail','') if isinstance(value,dict) else str(value)
            rows.append([label,r['codigo'],r.get('error',detail),r['hora']])
        view(folder,'resumen-antes.html','S07 · Comprobaciones iniciales',table(['Petición','HTTP (0 = sin conexión)','Detalle','Hora original'],rows),['antes.json'])
    if (folder/'gateway-y-balanceo.json').exists():
        g=read(folder,'gateway-y-balanceo.json')
        rows=[[label,r['codigo'],next((v for k,v in r['cabeceras'].items() if k.lower()=='x-instancia'),'No aparece'),r['hora'][11:19]] for label,r in [(k,g[k]) for k in ['alta','consulta','404-cuenta','404-gateway']]]
        content=table(['Petición','HTTP','X-Instancia','Hora'],rows)+'<h2>Nueve consultas a la misma cuenta</h2>'
        content+=table(['Consulta','HTTP','Instancia'],[[i,r['codigo'],next((v for k,v in r['cabeceras'].items() if k.lower()=='x-instancia'),'') ] for i,r in enumerate(g['nueve'],1)])
        view(folder,'resumen-gateway.html','S07 · Gateway y balanceo',content,['gateway-y-balanceo.json'])
    if (folder/'caida-medida.json').exists():
        d=read(folder,'caida-medida.json');samples=d['muestras']
        content=table(['Medición','Resultado'],[['Apagado abrupto',d['apagado']],['Salida del registro',d.get('salida-del-registro','No observada')],['Errores / consultas',str(d['errores'])+' / '+str(len(samples))]])
        errors=[r for r in samples if r['codigo']!=200]
        selected=(errors[:4] if errors else samples[:4])+samples[-4:]
        content+='<h2>Muestra: primeros errores y últimas respuestas</h2>'+table(['Hora','HTTP','Réplicas en registro','Duración (s)'],[[r['hora'][11:23],r['codigo'],r.get('replicas-en-registro',''),r['segundos']] for r in selected])
        view(folder,'resumen-caida.html','S07 · Caída medida de una réplica',content,['caida-medida.json','prediccion-caida.txt'])
    if (folder/'resumen-demo.json').exists():
        d=read(folder,'resumen-demo.json')
        content=table(['Cuenta','CLABE','Saldo consultado al terminar'],[[n,v['clabe'],v['saldo']] for n,v in d['saldos'].items()])
        content+='<h2>Extracto literal de la demo</h2><pre>'+esc(d['extracto'])+'</pre>'
        view(folder,'resumen-demo.html','S07 · Transferencia, SPEI y saldos',content,['resumen-demo.json','demo-v06.txt','cuenta-demo.log'])
elif semana=='s08':
    for phase,title in [('problema-antes','Problema antes de la saga'),('compensacion','Compensación aplicada'),('compensacion-pendiente','Devolución pendiente')]:
        if not (folder/(phase+'.json')).exists():continue
        d=read(folder,phase+'.json');content=table(['Respuesta HTTP','Estado','Fecha y hora'],[respuesta(d['transferencia'])])
        if 'antes' in d:
            after=dict(saldos(d['despues'])) if 'despues' in d else {}
            content+=table(['Cuenta','Saldo antes','Saldo después'],[[n,v,after.get(n,'Servicio apagado')] for n,v in saldos(d['antes'])])
        if 'cargada' in d:
            c=body(d['cargada']);content+='<h2>Antes del apagado</h2>'+table(['Transferencia','Estado observado','Hora'],[[c['id'],c['estado'],d['cargada']['hora']]])
        if 'apagado' in d:content+='<p>Apagado de cuenta: '+esc(d['apagado'])+'</p>'
        if 'movimientos' in d:
            content+='<h2>Asientos conservados</h2>'+table(['Tipo','Monto','Referencia'],[[x['tipo'],x['monto'],x.get('referencia','')] for x in body(d['movimientos'])])
        content+='<h2>Resultado de transferencia</h2>'+dump(body(d['transferencia']))
        view(folder,'resumen-'+phase+'.html','S08 · '+title,content,[phase+'.json'])
    if (folder/'circuito.log').exists():
        logs=(folder/'circuito.log').read_text();lines=[s for s in logs.splitlines() if 'Circuito cuenta:' in s]
        r=read(folder,'recuperacion.json');samples=read(folder,'circuito-abierto.json')
        content='<h2>Transiciones del circuito en los logs</h2><pre>'+esc('\n'.join(lines))+'</pre>'
        content+=table(['Solicitud con cuenta apagada','HTTP','Duración (s)'],[[i,x['codigo'],x['segundos']] for i,x in enumerate(samples,1)])
        content+='<h2>Sonda al recuperarse cuenta</h2>'+table(['HTTP','Estado','Hora'],[respuesta(r['sonda'])])
        view(folder,'resumen-circuito.html','S08 · Apertura y recuperación',content,['circuito.log','circuito-abierto.json','recuperacion.json'])
else:
    for phase,title in [('problema','Aviso síncrono falla'),('publicacion','Publicación sin esperar al consumidor')]:
        if not (folder/(phase+'.json')).exists():continue
        d=read(folder,phase+'.json');state=body(d.get('estado',d['transferencia'])).get('estado','')
        content=table(['HTTP transferencia','Estado','Hora'],[[d['transferencia']['codigo'],state,d['transferencia']['hora']]])
        if 'antes' in d:
            after=dict(saldos(d['despues']));content+=table(['Cuenta','Saldo antes','Saldo después'],[[n,v,after[n]] for n,v in saldos(d['antes'])])
        if 'exchange' in d:content+=table(['Consulta al broker','HTTP'],[['Exchange transferencias',d['exchange']['codigo']],['Cola todavía sin declarar',d['cola']['codigo']]])
        content+='<h2>Respuesta guardada</h2>'+dump(body(d.get('estado',d['transferencia'])))
        view(folder,'resumen-'+phase+'.html','S09 · '+title,content,[phase+'.json'])
    if (folder/'tres-ready.json').exists():
        d=read(folder,'tres-ready.json');q=body(d['cola']);content=table(['Cola','Ready','Unacked','Consumidores'],[[q['name'],q.get('messages_ready'),q.get('messages_unacknowledged'),q.get('consumers')]])
        content+='<h2>Tres transferencias con notificación apagada</h2>'+table(['HTTP','Estado','Hora'],[respuesta(x) for x in d['transferencias']])
        view(folder,'resumen-tres-ready.html','S09 · Tres mensajes esperan al consumidor',content,['tres-ready.json'])
    if (folder/'bandeja.json').exists():
        d=read(folder,'bandeja.json');q=body(d['cola']);content=table(['Cola','Mensajes restantes'],[[q['name'],q['messages']]])
        for name in ['origen','destino']:
            content+='<h2>Bandeja de '+name+'</h2>'+table(['ID','Aviso'],[[x['id'],x['mensaje']] for x in body(d[name])])
        view(folder,'resumen-bandeja.html','S09 · Consumo al volver notificación',content,['bandeja.json'])
    if (folder/'duplicado-despues.json').exists():
        content=''
        for phase,label in [('duplicado-antes','Antes: sin idempotencia'),('duplicado-despues','Después: evento único en la misma transacción')]:
            d=read(folder,phase+'.json');e=d['evento_republicado'];content+='<h2>'+label+'</h2>'+table(['Evento','Cuenta origen','Avisos origen','Avisos destino'],[[e['eventoId'],e['clabeOrigen'],len(body(d['final_origen'])),len(body(d['final_destino']))]])
        content+='<p>Se usan cuentas nuevas para el segundo experimento; los avisos históricos del primero se conservan.</p>'
        view(folder,'resumen-duplicado.html','S09 · Repetición del mismo evento',content,['duplicado-antes.json','duplicado-despues.json'])
    if (folder/'dlq.json').exists():
        d=read(folder,'dlq.json');before=read(folder,'veneno-antes.json');message=body(d['mensaje'])[0];death=message['properties']['headers']['x-death'][0]
        lines=[line for line in before['logs'].splitlines() if 'Mensaje inválido' in line]
        content='<h2>Antes: el mensaje se reencolaba</h2><pre>'+esc('\n'.join(lines[-2:]))+'</pre>'
        content+='<h2>Después: rechazo sin reencolar</h2>'+table(['DLQ','Mensaje','x-death reason'],[[body(d['dlq'])['name'],message['payload'],death['reason']]])
        content+='<h2>Una transferencia válida posterior sigue funcionando</h2>'+table(['HTTP','Estado','Hora'],[respuesta(d['transferencia_normal'])])
        view(folder,'resumen-dlq.html','S09 · Mensaje envenenado y DLQ',content,['veneno-antes.json','dlq.json'])
    if (folder/'revertida.json').exists():
        d=read(folder,'revertida.json');content=table(['HTTP','Estado','Hora'],[respuesta(d['transferencia'])])
        notices=[x for x in body(d['aviso']) if 'devolvimos' in x['mensaje']]
        content+=table(['Aviso','Fecha'],[[x['mensaje'],x['fecha']] for x in notices])
        view(folder,'resumen-revertida.html','S09 · Aviso de TransferenciaRevertida',content,['revertida.json'])
print('Vistas listas:',semana)
