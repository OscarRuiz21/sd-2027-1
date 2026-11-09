"""Visor local de resultados existentes: no ejecuta ni inventa experimentos."""
from pathlib import Path
import json,html
r=Path(__file__).parent/'evidencias'
def items(d,t):return [x['datos'] for x in d['registros'] if x['tipo']==t]
def cmds(d,pat):return [x for x in items(d,'comando') if pat in ' '.join(x['args'])]
def pretty(x):return json.dumps(x,ensure_ascii=False,indent=2)
def page(name,title,blocks,source):
 d=json.loads((r/(source+'.json')).read_text());assert d.get('resultado')=='OK'
 body=''.join('<h2>'+html.escape(h)+'</h2><pre>'+html.escape(t if isinstance(t,str) else pretty(t))+'</pre>' for h,t in blocks)
 s='''<!doctype html><html lang="es"><meta charset="utf-8"><title>S11 · Evidencia de ejecución</title><style>
*{box-sizing:border-box}body{margin:0;background:#eef3f6;color:#183447;font:19px/1.5 system-ui,sans-serif}main{max-width:1000px;margin:24px auto;background:white;border:1px solid #d4e0e7;padding:32px}h1{font-size:30px;line-height:1.2;margin:8px 0 12px}h2{font-size:20px;color:#087985;margin:22px 0 8px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:17px/1.5 monospace;background:#f1f5f7;padding:18px;border-left:4px solid #087985;margin:0}.meta{font-size:15px;color:#496472}.ok{color:#08714d;font-weight:bold}footer{font-size:15px;border-top:1px solid #d4e0e7;margin-top:24px;padding-top:14px}</style><main><div class="meta">Derek Santana · S11 · Visor de registros locales</div>'''
 s+='<h1>'+html.escape(title)+'</h1><div class="ok">Ejecución comprobada: OK</div><div class="meta">Fuente: '+source+'.json<br>Inicio UTC: '+d['fecha_utc']+'</div>'+body
 s+='<footer>Vista construida desde respuestas y comandos reales. Ejecución asistida con Codex; no es una sesión de Terminal ni evidencia de asistencia a clase.</footer></main></html>'
 (r/(name+'.html')).write_text(s)
if (r/'base.json').exists():
 d=json.loads((r/'base.json').read_text())
 if d.get('resultado')=='OK':
  rs=items(d,'http');page('captura-base','Punto de partida: consultas sin caché',[
   ('Diez GET de saldo',{'respuestas':[x['status'] for x in rs[-10:]],'lecturas_postgres':items(d,'lecturas_postgres')[0]}),
   ('Redis inicial','\n'.join(line for line in cmds(d,'INFO stats')[0]['stdout'].splitlines() if line.startswith(('keyspace_hits:','keyspace_misses:')))),('MongoDB inicial',cmds(d,'countDocuments')[0]['stdout']),('Una respuesta real',rs[-1]['body'])],'base')
if (r/'cache.json').exists():
 d=json.loads((r/'cache.json').read_text())
 if d.get('resultado')=='OK':page('captura-cache','Una caché compartida por tres réplicas',[
  ('Seis consultas',{'instancias':items(d,'instancias')[0],'lecturas_postgres':items(d,'lecturas_postgres')[0],'leidaEn':[x['body']['leidaEn'] for x in items(d,'http')[:6]]}),
  ('Redis: JSON, TTL y borrado después de transferencia',{'valor':json.loads(cmds(d,'GET cuentas::')[0]['stdout']),'TTL':cmds(d,'TTL')[0]['stdout'],'EXISTS_tras_transferencia':cmds(d,'EXISTS')[0]['stdout']})],'cache')
if all((r/(n+'.json')).exists() for n in ['medidas-redis','medidas-none']):
 d=json.loads((r/'medidas-redis.json').read_text());n=json.loads((r/'medidas-none.json').read_text())
 if d.get('resultado')==n.get('resultado')=='OK':
  rows=items(d,'latencia')+items(n,'latencia');lines=['tipo    corrida   media ms   mediana ms   p95 ms   lecturas SQL']
  for x in rows:lines.append(f"{x['tipo']:7} {x['corrida']:5} {x['promedio_ms']:10.2f} {x['mediana_ms']:12.2f} {x['p95_ms']:8.2f} {x['lecturas_postgres']:8}")
  page('captura-medidas','Hit ratio y latencia: todas las corridas',[
   ('Cien lecturas por escenario',items(d,'hit_ratio')),('Cien lecturas por corrida; p95 por rango más próximo','\n'.join(lines))],'medidas-redis')
if (r/'mongo.json').exists():
 d=json.loads((r/'mongo.json').read_text())
 if d.get('resultado')=='OK':
  docs=json.loads(cmds(d,'find().sort')[0]['stdout']);forms={x['canal']:x for x in docs}
  page('captura-avisos','Tres formas de aviso en MongoDB',[(k,forms[k]) for k in ['SMS','CORREO','PUSH']],'mongo')
  plans=[json.loads(x['stdout']) for x in cmds(d,'executionStats')]
  summary=[{k:x['executionStats'][k] for k in ['nReturned','totalDocsExamined','totalKeysExamined']} for x in plans]
  avisos=[x for x in items(d,'http') if isinstance(x['body'],list)][0]
  page('captura-indices','Índice, diez avisos y evento duplicado',[
   ('Endpoint por CLABE',{'status':avisos['status'],'avisos':len(avisos['body'])}),('Índices registrados','\n'.join(x['name']+': '+json.dumps(x['key']) for x in json.loads(cmds(d,'getIndexes')[0]['stdout']))),
   ('Planes: primero índice; segundo recorrido natural','\n'.join(json.dumps(x) for x in summary)),('Mismo evento publicado dos veces: documentos guardados',cmds(d,'countDocuments')[0]['stdout'])],'mongo')
if (r/'viejo.json').exists():
 d=json.loads((r/'viejo.json').read_text())
 if d.get('resultado')=='OK':
  obs=items(d,'dato_viejo');lines=['segundos    TTL    saldo mostrado    leidaEn']
  for x in obs:lines.append(f"{x['segundos']:7.1f} {x['ttl']:>6} {str(x['respuesta']['body']['saldo']):>16}    {x['respuesta']['body']['leidaEn']}")
  rej=items(d,'rechazo_saldo_real')[0]
  page('captura-viejo','La copia vieja no autoriza un cargo',[
   ('Saldo SQL cambiado a 5; intento de transferir 100',{'HTTP':rej['status'],'respuesta':rej['body']}),('Evolución observada','\n'.join(lines))],'viejo')
print('Visores generados desde resultados OK')
