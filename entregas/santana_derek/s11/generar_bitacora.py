"""Genera un PDF desde registros reales; no inventa resultados ausentes.
Dependencias: reportlab, pillow. Las figuras son vistas de JSON, no capturas de Terminal.
"""
from pathlib import Path
import json,textwrap
from xml.sax.saxutils import escape
from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,PageBreak,Table,TableStyle,Image
from reportlab.lib.styles import getSampleStyleSheet,ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from PIL import Image as PILImage,ImageDraw,ImageFont
r=Path(__file__).parent;e=r/'evidencias'
def load(n):
 d=json.loads((e/(n+'.json')).read_text());assert d.get('resultado')=='OK',n;return d
base,cache,med,onoff,mongo,viejo=[load(n) for n in ['base','cache','medidas-redis','medidas-none','mongo','viejo']]
def items(d,t):return [x['datos'] for x in d['registros'] if x['tipo']==t]
def commands(d,needle):return [x for x in items(d,'comando') if needle in ' '.join(x['args'])]
styles=getSampleStyleSheet();styles.add(ParagraphStyle(name='BodyD',fontName='Helvetica',fontSize=10,leading=15,spaceAfter=10,textColor=colors.HexColor('#253b4b')))
styles['Title'].textColor=colors.HexColor('#123b50');styles['Heading1'].textColor=colors.HexColor('#087d89')
story=[]
def p(t):story.append(Paragraph(escape(t),styles['BodyD']))
def h(t):story.append(Paragraph(escape(t),styles['Heading1']))
def table(rows,widths=None):
 cells=[[Paragraph(escape(str(x)),styles['BodyD']) for x in row] for row in rows]
 t=Table(cells,colWidths=widths,repeatRows=1,hAlign='LEFT');t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e1eff1')),('VALIGN',(0,0),(-1,-1),'TOP'),('LINEBELOW',(0,0),(-1,-1),.3,colors.HexColor('#cddade')),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7)]));story.extend([t,Spacer(1,12)])
def figure(name,title,lines):
 # Todas las líneas provienen de los JSON de ejecución y se identifican como vistas.
 lines=[title,'Vista generada desde los registros JSON de esta ejecución.','']+list(lines)
 lines=[w for line in lines for w in (textwrap.wrap(str(line),width=105) or [''])]
 im=PILImage.new('RGB',(1280,50+26*len(lines)),'#f0f5f7');draw=ImageDraw.Draw(im)
 font=ImageFont.truetype('/System/Library/Fonts/Menlo.ttc',17)
 for i,line in enumerate(lines):draw.text((22,20+i*26),line,font=font,fill='#17384a')
 path=e/(name+'.png');im.save(path);story.append(Image(str(path),width=480,height=im.height*480/im.width));story.append(Spacer(1,12))
def page():story.append(PageBreak())
def footer(canvas,doc):
 canvas.setFont('Helvetica',8);canvas.setFillColor(colors.HexColor('#506a77'));canvas.drawString(42,25,'Derek Santana Cardoso | S11 | Ejecución asistida y evidencia trazable');canvas.drawRightString(553,25,str(doc.page))
story.append(Paragraph('Tres almacenes, una verdad para el saldo',styles['Title']))
p('Sistemas Distribuidos 2027-1 · Derek Santana Cardoso · S11')
p('Preparado con Codex. Las pruebas fueron ejecutadas por el agente en el entorno local; no se atribuyen a participación en clase ni a una ejecución personal de Derek. El repaso personal queda pendiente. Fechas UTC conservadas en cada JSON.')
p('Base docente 06-poliglota-base, commit 9d10c2cdfce2d8d6d171d655bc680b6d2a1e23be. PostgreSQL conserva cuentas, movimientos y transferencias. Redis guarda una copia para mostrar. MongoDB conserva los avisos con campos distintos por canal.')
h('1. Punto de partida')
p('Se reprodujo la consulta sin caché (CACHE_TIPO=none) en el entorno aislado. Las cuentas se crearon por HTTP. Se usó una réplica en este punto inicial y tres en las comparaciones posteriores. El conteo se obtuvo por diferencia entre líneas «leida de Postgres» antes y después de diez GET.')
figure('vista-base','Sin caché',[f"Diez GET; lecturas PostgreSQL: {items(base,'lecturas_postgres')[0]}",f"MongoDB, avisos iniciales: {commands(base,'countDocuments')[0]['stdout'].strip()}",'Redis inicial: '+', '.join(line for line in commands(base,'INFO stats')[0]['stdout'].splitlines() if line.startswith(('keyspace_hits:','keyspace_misses:'))),f"Registro UTC: {base['fecha_utc']}"])
page();h('2. Caché compartida e invalidación')
p('ConfiguracionCache habilita la caché cuentas, JSON de CuentaVista y TTL de 30 s. ConsultaDeSaldo usa Cacheable. La autorización del cargo sigue pasando por CuentaService y PostgreSQL. Separar clases evita depender de la autoinvocación que omite el proxy de Spring.')
p('CacheEvict en cargo/abono borra la copia después del commit con transactionAware(). Invalidar antes permitiría repoblar con el saldo anterior antes de confirmarse el cambio. Se usa DEL para que la siguiente lectura reconstruya la vista desde la fuente de verdad. Esto reduce carreras, pero no convierte cache-aside en consistencia estricta: una lectura concurrente puede repoblar una copia atrasada hasta su TTL.')
rs=items(cache,'http')[:6]
figure('vista-cache','Seis consultas, tres procesos',[f"Instancias: {', '.join(str(x) for x in items(cache,'instancias')[0])}",f"Lecturas PostgreSQL: {items(cache,'lecturas_postgres')[0]}",f"leidaEn de las seis respuestas: {rs[0]['body']['leidaEn']}",f"TTL: {commands(cache,'TTL')[0]['stdout']}",f"EXISTS tras transferencia: {commands(cache,'EXISTS')[0]['stdout']}",commands(cache,'GET cuentas::')[0]['stdout']])
page();h('3. Medidas reales')
ratios=items(med,'hit_ratio')
table([['Escenario','Hits','Misses','Hit ratio','Lecturas SQL']]+[[x['escenario'],x['keyspace_hits'],x['keyspace_misses'],f"{x['ratio']:.3%}",x['lecturas_postgres']] for x in ratios],[85,65,65,110,155])
p('A: cien consultas consecutivas. B: cien consultas con una transferencia de un peso cada diez. Se vació la llave y se reiniciaron estadísticas antes de cada escenario. Las invalidaciones y cualquier expiración durante la prueba producen nuevos misses. Los GET auxiliares de Redis quedan fuera de estos intervalos. Las estadísticas sí incluyen consultas internas de Transferencia que usen esta caché.')
lat=items(med,'latencia')+items(onoff,'latencia')
table([['Caché / corrida','Media ms','Mediana ms','p95 ms','Lecturas SQL']]+[[x['tipo']+' / '+str(x['corrida']),f"{x['promedio_ms']:.2f}",f"{x['mediana_ms']:.2f}",f"{x['p95_ms']:.2f}",x['lecturas_postgres']] for x in lat],[110,90,90,85,105])
p('Cien respuestas por corrida, tres corridas por configuración; p95 por rango más próximo (posición 95 ordenada). Se mide HTTP extremo a extremo por el gateway con reloj monotónico. No es un benchmark controlado: la laptop ejecuta varios contenedores y la base puede tener datos en memoria. La reducción de lecturas SQL mide el trabajo evitado; la tabla permite contrastarlo con la latencia sin asumir que siempre mejora.')
figure('vista-medidas','Resultados de las seis corridas',[f"{x['tipo']} #{x['corrida']}: n=100; p95={x['p95_ms']:.2f} ms; SQL={x['lecturas_postgres']}" for x in lat])
page();h('4. Avisos en MongoDB')
p('Cada aviso tiene _id eventoId:clabe, campos comunes y forma por canal. Monto y saldo usan Decimal128 y fecha proviene del evento. El consumidor guarda solo en Mongo para transferencias; no añade una doble escritura a la bandeja SQL. Insertar cada destinatario e ignorar DuplicateKey permite completar un evento interrumpido a la mitad.')
docs=json.loads(commands(mongo,'find().sort')[0]['stdout']);forms={d['canal']:d for d in docs}
figure('vista-avisos','Tres formas de documento',[json.dumps(forms[k],ensure_ascii=False) for k in ['SMS','CORREO','PUSH']])
httpavisos=[x for x in items(mongo,'http') if isinstance(x['body'],list)][0]
p(f"El endpoint devolvió {len(httpavisos['body'])} avisos para Beto después de más de diez transferencias. El código filtra CLABE, ordena fecha descendente y limita a diez. Fechas iguales no tienen desempate adicional garantizado.")
indices=commands(mongo,'getIndexes')[0]['stdout'];p('Índice registrado: '+indices)
page();h('5. Planes de consulta y duplicado')
plans=[json.loads(x['stdout']) for x in commands(mongo,'executionStats')]
figure('vista-indices','Con y sin índice',[f"Consulta {i+1}: docs={x['executionStats']['totalDocsExamined']}; keys={x['executionStats']['totalKeysExamined']}; n={x['executionStats']['nReturned']}; plan={json.dumps(x['queryPlanner']['winningPlan'])}" for i,x in enumerate(plans)])
p('La segunda consulta fuerza hint natural: sirve para contrastar el recorrido de documentos y ordenamiento con el índice clabe_fecha. Los planes completos permanecen en mongo.json.')
count=commands(mongo,'countDocuments')[0]['stdout'];figure('vista-duplicado','Mismo evento publicado dos veces',[f"Avisos del evento fijo: {count}",'El evento tiene origen y destino distintos: se esperan dos documentos, no cuatro.'])
p('La unicidad pertenece a cada aviso. El ack se emite al terminar registrar. Si se pierde antes del ack, RabbitMQ puede reenviar; los _id ya guardados no se duplican. Las pruebas unitarias también cubren el caso en que solo el primer destinatario estaba guardado.')
page();h('6. La copia vieja no autoriza el cargo')
obs=items(viejo,'dato_viejo');rej=items(viejo,'rechazo_saldo_real')[0]
table([['Segundos desde lectura','TTL','Saldo mostrado']]+[[f"{x['segundos']:.1f}",x['ttl'],x['respuesta']['body']['saldo']] for x in obs],[190,100,190])
p(f"Se cambió el saldo SQL a 5 por fuera de Cuenta, omitiendo invalidación, y se intentó transferir 100 mientras la vista podía estar vieja. Respuesta HTTP {rej['status']}: {json.dumps(rej['body'],ensure_ascii=False)}")
figure('vista-viejo','Evolución de la copia',[f"t={x['segundos']:.1f}s TTL={x['ttl']} saldo={x['respuesta']['body']['saldo']} leidaEn={x['respuesta']['body']['leidaEn']}" for x in obs])
p('La observación está muestreada: el cambio real ocurrió entre dos consultas, no se afirma un instante exacto de expiración. El script restaura el saldo original en finally e invalida la copia. La edición SQL fue un experimento y no una operación de negocio.')
page();h('7. Respuestas de cierre y límites')
p('¿Por qué Mongo para avisos y no para saldo? Los avisos se leen completos por CLABE y tienen campos específicos de canal; el saldo requiere las invariantes y transacciones ya implementadas en Cuenta. Mongo también ofrece transacciones: no se afirma que sea incapaz de manejar dinero. Aquí cambiar de motor no aporta una mejora que compense rehacer esas reglas.')
p(f"¿Qué se ganó con la caché? Hit ratio A={ratios[0]['ratio']:.2%} y B={ratios[1]['ratio']:.2%}. En tres corridas de cien lecturas, Redis tuvo media conjunta {sum(x['promedio_ms'] for x in lat[:3])/3:.2f} ms y {sum(x['lecturas_postgres'] for x in lat[:3])} lecturas SQL; sin caché, {sum(x['promedio_ms'] for x in lat[3:])/3:.2f} ms y {sum(x['lecturas_postgres'] for x in lat[3:])} lecturas SQL. Las invalidaciones de B vuelven a consultar SQL. La latencia media no mejoró en estas corridas; el beneficio medido fue reducir el trabajo SQL. No mide capacidad máxima ni garantiza que toda carga mejore.")
p('¿Si la autorización leyera caché? Podría aprobar un cargo con saldo que ya no existe. Aquí la transferencia consultó la fuente SQL y no convirtió la copia vieja en autorización.')
p('¿Por qué DEL tras commit? Obliga a reconstruir desde datos confirmados y evita publicar un saldo que todavía podría revertirse. Un SET atrasado puede sobrescribir una copia más nueva con el resultado de una operación anterior. DEL reduce ese riesgo, pero no elimina toda carrera de cache-aside: el TTL limita la vida de una entrada, no garantiza un snapshot global.')
p('Reto Redis caído: no implementado. En esta versión una falla de invalidación posterior al commit puede producir una respuesta ambigua. La solución completa tendría que tratar también el callback diferido, no solo suponer que CacheErrorHandler basta.')
p('Incidencias previas conservadas: una conexión fría a Redis excedió 500 ms; después, el timeout de 2 s de la saga venció aunque el cargo de un peso sí se confirmó. Se devolvió manualmente con llave idempotente en los datos de laboratorio y se verificaron los saldos originales. El compose usa 10 s para Transferencia. Ampliar el plazo no resuelve la ambigüedad general: ese límite de la base docente sigue documentado en README y en los registros del fallo.')
p('Evidencias: base.json, cache.json, medidas-redis.json, medidas-none.json, mongo.json, viejo.json y registros de Maven verify (10 pruebas de cuenta y 19 de notificación, sin fallos). Las figuras de este PDF son vistas generadas de esos registros reales, no capturas de una sesión de Terminal ni participación de clase. Fuente del contrato: github.com/OscarRuiz21/sd-2027-1, labs/s11-poliglota/README.md.')
for filename,title in [('captura-base.jpg','Captura: punto de partida'),('captura-cache.jpg','Captura: caché compartida'),('captura-medidas.jpg','Captura: medidas'),('captura-avisos-sms.jpg','Captura: aviso SMS'),('captura-avisos-correo.jpg','Captura: aviso por correo'),('captura-avisos-push.jpg','Captura: aviso push'),('captura-indices.jpg','Captura: índice y duplicado'),('captura-viejo.jpg','Captura: dato viejo'),('captura-eureka.jpg','Captura: directorio de servicios')]:
    path=e/filename
    if not path.exists():continue
    page();h(title)
    p('Captura real del navegador. El visor muestra registros guardados de la ejecución. No se atribuye a una sesión de Terminal ni a participación en clase.')
    with PILImage.open(path) as im:w,himg=im.size
    scale=min(500/w,610/himg)
    story.append(Image(str(path),width=w*scale,height=himg*scale))
SimpleDocTemplate(str(r/'bitacora-s11.pdf'),pagesize=(595,842),leftMargin=42,rightMargin=42,topMargin=40,bottomMargin=45).build(story,onFirstPage=footer,onLaterPages=footer)
print(r/'bitacora-s11.pdf')
