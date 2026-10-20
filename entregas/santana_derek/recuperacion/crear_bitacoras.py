"""Genera los PDF únicamente a partir de experimentos y capturas que sí existen."""
import json
import sys
from datetime import datetime
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, PageBreak, Table, TableStyle, Preformatted
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon

BASE=Path(__file__).resolve().parents[1]
NAVY=colors.HexColor('#12314a');TEAL=colors.HexColor('#087e8b')
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='Texto',fontName='Helvetica',fontSize=10.5,leading=15,spaceAfter=10,textColor=NAVY))
styles.add(ParagraphStyle(name='TituloLab',fontName='Helvetica-Bold',fontSize=25,leading=30,spaceAfter=16,textColor=NAVY))
styles.add(ParagraphStyle(name='SeccionLab',fontName='Helvetica-Bold',fontSize=15,leading=20,spaceAfter=12,textColor=TEAL))
styles.add(ParagraphStyle(name='PieImagen',fontName='Helvetica',fontSize=8.5,leading=12,spaceAfter=14,textColor=colors.HexColor('#4b6275')))
styles.add(ParagraphStyle(name='CodigoLab',fontName='Courier',fontSize=8,leading=11,spaceAfter=14,textColor=NAVY,backColor=colors.HexColor('#f0f5f8'),borderPadding=7))

class Bitacora:
 def __init__(self,semana,titulo):
  self.root=BASE/semana;self.e=self.root/'evidencias';self.semana=semana;self.story=[]
  self.p(semana.upper()+' | SISTEMAS DISTRIBUIDOS 2027-1','PieImagen')
  self.p(titulo,'TituloLab')
  self.p('Derek Santana Cardoso · Grupo 2 · 19 y 20 de octubre de 2026')
  self.p('Bitácora de ejecución asistida. Experimentos automatizados en esta máquina con ayuda de Codex. Las respuestas HTTP indican hora de Ciudad de México; los logs de los contenedores conservan UTC. Las capturas del panel de evidencias muestran registros originales guardados; se identifican como tales y no se presentan como capturas de Terminal. Revisión y explicación personal de Derek pendientes.','PieImagen')
 def data(self,n):return json.loads((self.e/n).read_text())
 def p(self,text,style='Texto'):self.story.append(Paragraph(escape(str(text)).replace('\n','<br/>'),styles[style]))
 def h(self,text):self.p(text,'SeccionLab')
 def page(self):self.story.append(PageBreak())
 def code(self,path,inicio,lineas):
  source=(self.root/path).read_text().splitlines()
  index=next(i for i,s in enumerate(source) if inicio in s)
  self.story.append(Preformatted('\n'.join(source[index:index+lineas]),styles['CodigoLab'],maxLineLength=88))
 def img(self,name,caption,ancho=480):
  path=self.e/name
  if not path.exists():raise FileNotFoundError(path)
  w,h=ImageReader(str(path)).getSize();scale=min(ancho/w,440/h)
  self.story.append(Image(str(path),width=w*scale,height=h*scale,hAlign='LEFT'))
  self.p(caption,'PieImagen')
 def table(self,rows):
  cells=[[Paragraph(escape(str(c)),styles['PieImagen']) for c in row] for row in rows]
  table=Table(cells,colWidths=[165,315],hAlign='LEFT')
  table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e6f1f3')),('VALIGN',(0,0),(-1,-1),'TOP'),('GRID',(0,0),(-1,-1),.4,colors.HexColor('#cad7e0')),('LEFTPADDING',(0,0),(-1,-1),9),('RIGHTPADDING',(0,0),(-1,-1),9),('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),4)]))
  self.story.extend([table,Spacer(1,14)])
 def saga(self):
  drawing=Drawing(480,220)
  def box(x,y,label):
   drawing.add(Rect(x,y,108,38,rx=5,fillColor=colors.HexColor('#e6f1f3'),strokeColor=TEAL))
   lines=label.split('|')
   for i,line in enumerate(lines):
    drawing.add(String(x+54,y+21+(len(lines)-1)*5-i*10,line,textAnchor='middle',fontName='Helvetica-Bold',fontSize=8,fillColor=NAVY))
  def arrow(x1,y1,x2,y2):
   drawing.add(Line(x1,y1,x2,y2,strokeColor=TEAL,strokeWidth=1.4))
   if y1==y2:points=[x2,y2,x2-5,y2+3,x2-5,y2-3]
   else:points=[x2,y2,x2-3,y2+5,x2+3,y2+5]
   drawing.add(Polygon(points,fillColor=TEAL,strokeColor=TEAL))
  for x,label in [(0,'INICIADA'),(124,'CARGADA'),(248,'ABONADA'),(372,'COMPLETADA')]:box(x,172,label)
  for x in [108,232,356]:arrow(x,191,x+16,191)
  box(124,89,'COMPENSANDO');box(272,89,'COMPENSADA');box(124,10,'COMPENSACION|PENDIENTE')
  arrow(178,172,178,127);arrow(232,108,272,108);arrow(178,89,178,48)
  drawing.add(String(190,145,'abono no aplicado',fontName='Helvetica',fontSize=8,fillColor=NAVY))
  drawing.add(String(190,65,'devolución falla',fontName='Helvetica',fontSize=8,fillColor=NAVY))
  self.story.extend([drawing,Spacer(1,12)])
 def finish(self):
  target=self.root/('bitacora-'+self.semana+'.pdf')
  def footer(canvas,doc):
   canvas.setStrokeColor(TEAL);canvas.line(55,48,540,48)
   canvas.setFont('Helvetica',8);canvas.setFillColor(NAVY)
   canvas.drawString(55,35,'Derek Santana Cardoso | '+self.semana.upper()+' | Ejecución asistida')
   canvas.drawRightString(540,35,str(doc.page))
  doc=SimpleDocTemplate(str(target),pagesize=A4,rightMargin=55,leftMargin=55,topMargin=48,bottomMargin=64)
  doc.build(self.story,onFirstPage=footer,onLaterPages=footer)
  print(target)

def s07():
 b=Bitacora('s07','Una puerta, un directorio\ny tres copias')
 antes=b.data('antes.json');gateway=b.data('gateway-y-balanceo.json');caida=b.data('caida-medida.json')
 b.h('1. Qué se rompió al partir el banco')
 b.p('En el monolito, cargo y abono podían pertenecer a la misma transacción. Al separarlos por HTTP, cada servicio confirma en su propia base. Si después del cargo falla el abono, la transacción de transferencia no puede deshacer por sí sola la escritura de cuenta. Se necesita un protocolo de negocio para terminar o compensar.')
 b.table([['Comprobación inicial','Resultado real'],['Cuenta inexistente',str(antes['cuenta']['codigo'])+'; hora '+antes['cuenta']['hora']],['POST directo a movimiento',str(antes['movimiento']['codigo'])+'; el endpoint interno existe'],['Directorio antes de habilitarlo',antes['directorio']['error']]])
 b.img('antes.png','Vista de antes.json: respuestas HTTP y conexión rechazada del directorio, con horarios originales.')
 b.page();b.h('2. Eureka y los clientes')
 directorio=b.data('directorio-cinco-servicios.json')
 b.p('Medición del registro: '+directorio['hora']+'. Los cinco servicios se registraron en Eureka. El XML de CUENTA se conserva en cuenta-eureka.xml: IP privada, puerto 8080, estado UP, intervalo de renovación y duración del lease. El directorio espera la expiración; no conoce instantáneamente una caída.')
 b.img('eureka-cinco.png','Dashboard real de Eureka, antes de arrancar el gateway, con los cinco servicios.')
 b.p('Respuesta a Piensa: registrarse no implica consultar el registro para cada llamada. Al principio, transferencia todavía usa una dirección resuelta por el DNS de Compose. La tarea agrega el RestClient.Builder con @LoadBalanced y utiliza nombres de servicio sin puerto.')
 b.page();b.h('3. La puerta y sus reglas')
 b.table([['Petición','Resultado real'],['Cuenta inexistente por gateway',str(gateway['404-cuenta']['codigo'])+'; responde cuenta y aparece X-Instancia'],['POST de movimiento por gateway',str(gateway['404-gateway']['codigo'])+'; no hay ruta pública para ese método'],['Alta y consulta',str(gateway['alta']['codigo'])+' / '+str(gateway['consulta']['codigo'])],['Hora del alta',gateway['alta']['hora']]])
 b.img('gateway.png','Vista del registro de pruebas del gateway; el archivo JSON completo conserva las cabeceras.')
 b.p('El 400 directo demuestra que el endpoint interno existía. El 404 de la cuenta nace después del enrutamiento; el 404 del POST de movimientos nace de las reglas del gateway. StripPrefix quita /api antes de reenviar. No se publican los puertos individuales de los servicios en la configuración final.')
 b.page();b.h('4. Tres copias y reparto')
 instancias=[next(v for k,v in x['cabeceras'].items() if k.lower()=='x-instancia') for x in gateway['nueve']]
 b.p('Nueve consultas consecutivas: '+', '.join(instancias)+'. Se observaron '+str(len(set(instancias)))+' identificadores distintos. Cada respuesta conserva su hora en gateway-y-balanceo.json. Las réplicas de aplicación comparten una misma base; no son tres bases independientes.')
 b.img('eureka-tres.png','Dashboard real con CUENTA UP (3).')
 b.h('5. Caída de una copia')
 b.p(caida['prediccion'])
 b.table([['Medición','Resultado'],['Apagado abrupto',caida['apagado']],['Registro pasa a dos',caida.get('salida-del-registro','No observado en la ventana')],['Respuestas fallidas',str(caida['errores'])+' de '+str(len(caida['muestras']))+' consultas']])
 b.page();b.h('5. Caída: observación y explicación')
 b.img('caida.png','Vista de las mediciones originales de caida-medida.json; se conserva cada respuesta, duración y recuento del registro.')
 b.p('La lista atraviesa varias capas: lease de Eureka, barrido, caché del servidor, actualización del cliente y caché del balanceador. Por eso la salida del registro y el fin de los errores pueden no coincidir. Las cifras de esta bitácora proceden del ensayo local, no del ejemplo docente.')
 b.h('6. Cierre')
 b.p('El directorio localiza instancias vivas. El gateway concentra la entrada y aplica reglas de rutas. El balanceador elige una instancia del conjunto conocido. En la ventana de caída se midieron '+str(caida['errores'])+' errores porque algunas peticiones podían usar una lista que aún incluía la réplica apagada.')
 b.page();b.h('7. Tarea: transferencia también reparte')
 b.p('Se habilitó un RestClient.Builder con @LoadBalanced, conservando los timeouts, y CUENTA_URL/MOVIMIENTO_URL/NOTIFICACION_URL usan los nombres sin puerto. La demo se ejecutó por el gateway. Sus salidas y las instancias que atendieron cargos/abonos se guardan en demo-v06.txt y cuenta-demo.log.')
 b.img('demo.png','Vista de la salida real de bash demo-v06.sh. La salida completa queda en el archivo original.')
 b.p('Fuentes: guía oficial Lab-S07-Mexi-Banco.html y ramas lab/03-gateway-discovery-balanceo y 03-gateway-discovery-balanceo de OscarRuiz21/mexi-banco. Versión y huella del archivo descargado en evidencias/fuente.json. Configuración guiada cotejada con la referencia docente; mediciones locales asistidas.','PieImagen')
 b.finish()

def s08():
 b=Bitacora('s08','La transferencia como saga\ny un circuito manual')
 problema=b.data('problema-antes.json');comp=b.data('compensacion.json');pend=b.data('compensacion-pendiente.json');rec=b.data('recuperacion.json')
 logs=(b.e/'circuito.log').read_text()
 assert 'SEMIABIERTO -> CERRADO' in logs,'Falta probar recuperación del circuito'
 b.h('1. El problema antes de arreglarlo')
 b.p('Se provocó la caída de cuenta entre cargo y abono en la base S07. Los saldos y la respuesta se guardan en problema-antes.json. El cargo confirmado pertenece a otra transacción; el fallo remoto no lo revierte. La pausa era de 20 s y el gateway base esperaba 15 s: devolvió 504 antes de acabar el intento. Para los ensayos siguientes se amplió su espera a 40 s, sin cambiar los timeouts del cliente de cuenta.')
 b.img('problema.png','Vista del experimento anterior a la saga, con saldos y respuesta reales.')
 b.page();b.h('2. Pasos, estados y compensación')
 b.saga()
 b.table([['Paso','Estado y decisión'],['Validar y guardar operación','INICIADA'],['Cargo confirmado','CARGADA'],['Abono confirmado','ABONADA; no devolver dinero ya recibido por destino'],['Asientos y avisos completados','COMPLETADA'],['Abono no aplicado','COMPENSANDO: devolver a origen'],['Devolución y asientos completos','COMPENSADA'],['No se puede devolver','COMPENSACION_PENDIENTE'],['Respuesta de escritura ambigua','CARGO_INCIERTO / ABONO_INCIERTO; conciliar']])
 b.p('Una devolución ya aplicada con asientos pendientes queda COMPENSADA_REGISTRO_PENDIENTE para no devolver otra vez. Los resultados ambiguos se concilian antes de repetir una escritura.')
 b.page();b.h('3. El código clave')
 b.p('TransferenciaService guarda cada transición con save en su propia transacción. La llamada completa no se encierra en @Transactional. compensar devuelve el monto a origen, registra el cargo y un DEPOSITO con referencia DEVOLUCION. El controlador devuelve 201 al completar, 409 al compensar y 202 cuando queda trabajo pendiente. Un timeout se trata como un resultado incierto.')
 b.p('Fragmentos de codigo/transferencia/src/main/java/mx/mexibanco/transferencia/TransferenciaService.java:','PieImagen')
 b.code('codigo/transferencia/src/main/java/mx/mexibanco/transferencia/TransferenciaService.java','if(!sinEfectoConfirmado(e)) return estado(t,"ABONO_INCIERTO"',3)
 b.code('codigo/transferencia/src/main/java/mx/mexibanco/transferencia/TransferenciaService.java','t=estado(t,"COMPENSANDO"',11)
 b.p('El ensayo inicial detectó que «No instances available for cuenta» nace en el balanceador antes de enviar HTTP. Se corrigió esa clasificación local y se añadieron pruebas para distinguirla de un timeout y de un 500 remoto. El intento inicial se conserva en intento-pendiente-inicial.json; no se sustituyó su resultado por el del ensayo corregido.')
 b.page();b.h('4. Compensación con cuenta encendida')
 t=comp['transferencia']
 b.p('Falla provocada antes de enviar el abono, mediante FALLAR_ABONO_A_CLABE. Hora de la respuesta: '+t['hora']+'. HTTP '+str(t['codigo'])+'; estado '+t['cuerpo']['estado']+'. Se consultaron de nuevo ambos saldos y los movimientos.')
 b.img('compensacion.png','Registro real del ensayo de compensación. El código de estado explica el resultado y los saldos muestran la devolución.')
 b.page();b.h('5. Cuando también falla la devolución')
 b.p('Se añadió una pausa entre cargo y abono y se apagó cuenta después de observar CARGADA. La operación quedó '+pend['transferencia']['cuerpo']['estado']+'. El registro conserva el momento del apagado y la respuesta final.')
 b.img('pendiente.png','Vista de compensacion-pendiente.json. La operación no queda abandonada en CARGADA.')
 b.p('La base S07 no acepta claves idempotentes para abonos; por eso esta entrega deja el pendiente persistido y no reintenta una devolución a ciegas. Para terminarla, primero conciliar si ya se aplicó; después agregar una llave fija transferencia-N-devolucion, almacenada con el abono y protegida por UNIQUE. Solo con ese contrato seguro se programa el reintento con espera creciente. Una caída entre respuesta remota y guardado local también exige conciliación.')
 b.page();b.h('6. Circuit breaker')
 b.p('CircuitoCuenta se implementó a mano: tres fallas de infraestructura abren el circuito; diez segundos después admite una sola sonda; el éxito lo cierra. Los errores de negocio no cuentan como caída. Las respuestas abiertas indican que cuenta no fue llamada; se conservaron sus duraciones. Las pruebas unitarias comprueban esos cambios y el rechazo sin invocar la operación.')
 b.img('circuito.png','Logs reales con CERRADO, ABIERTO y SEMIABIERTO. La recuperación también se verificó con una transferencia HTTP '+str(rec['sonda']['codigo'])+'.')
 b.h('7. Lo que la saga no ofrece')
 b.p('No ofrece aislamiento global. Entre el cargo y su compensación, una consulta puede ver el saldo temporalmente reducido. Devolver más tarde no elimina esa observación previa ni deshace automáticamente decisiones de otros procesos. Los estados permiten reconocer el trabajo pendiente; no convierten varias bases en una sola transacción ACID.')
 b.p('Base: OscarRuiz21/mexi-banco, rama 03-gateway-discovery-balanceo. Código propio asistido del circuito y la saga en codigo/transferencia. Fuente y versión en evidencias/fuente.json. El enunciado permite documentar la terminación de la compensación pendiente como mínimo obligatorio.','PieImagen')
 b.finish()

def s09():
 b=Bitacora('s09','Que el aviso espere\nsin tumbar la transferencia')
 problema=b.data('problema.json');pub=b.data('publicacion.json');ready=b.data('tres-ready.json');band=b.data('bandeja.json')
 antes=b.data('duplicado-antes.json');despues=b.data('duplicado-despues.json');dlq=b.data('dlq.json');revertida=b.data('revertida.json')
 b.h('1. El problema del aviso síncrono')
 b.p('Con notificación apagada, la base docente movió el dinero, respondió HTTP '+str(problema['transferencia']['codigo'])+' y dejó la transferencia en '+problema['estado']['cuerpo']['estado']+'. Los avisos por HTTP todavía eran parte del camino que esperaba el cliente.')
 b.img('problema.png','Vista de problema.json: estado y saldos antes de modificar la publicación.')
 b.page();b.h('2. Publicación del hecho')
 b.p('TransferenciaService guarda el estado final y PublicadorDeEventos envía el contrato JSON mediante RabbitTemplate. Se usa el exchange topic durable transferencias, las dos routing keys del enunciado y mensajes persistentes. El eventoId es estable para la misma transferencia y tipo.')
 b.p('Con notificación apagada, la versión modificada respondió HTTP '+str(pub['transferencia']['codigo'])+' y estado '+pub['transferencia']['cuerpo']['estado']+'. En esta fase todavía no existía cola, de modo que aceptar el evento en el exchange no garantizaba un aviso guardado.')
 b.img('publicacion.png','Vista de la publicación con consumidor apagado y sin cola declarada todavía.',430)
 b.img('rabbit-exchange.png','Panel real: exchange transferencias de tipo topic y durable.',430)
 b.page();b.h('3. Desacople en tiempo')
 b.p('Con la cola declarada y el consumidor apagado se hicieron tres transferencias. El broker informó '+str(ready['cola']['cuerpo']['messages_ready'])+' mensajes Ready. Al volver notificación, la cola quedó vacía y la bandeja de cada cuenta recibió tres avisos. Se confirma después del commit de GuardarAvisos.')
 b.img('rabbit-ready.png','Panel real de RabbitMQ con tres mensajes Ready durante la caída del consumidor. Las respuestas de las tres transferencias están en tres-ready.json.')
 b.img('bandeja.png','Vista del consumo posterior, con la cola vacía y los avisos guardados.')
 b.page();b.h('4. Duplicados: antes y después')
 b.p('Primero se deshabilitó la idempotencia solo para reproducir el fallo. Un evento se volvió a publicar con el mismo eventoId; la bandeja de origen terminó con '+str(len(antes['final_origen']['cuerpo']))+' avisos del mismo hecho. Después se activó el registro de evento_recibido y se repitió con cuentas nuevas para no confundir el historial previo: tras varios replays, origen conservó '+str(len(despues['final_origen']['cuerpo']))+' aviso.')
 b.p('La clave primaria del evento se inserta con ON CONFLICT DO NOTHING en la misma transacción que los avisos. Si la clave ya existe, el consumidor confirma sin volver a guardar. La restricción también protege frente a consumidores simultáneos.')
 b.img('duplicado.png','Comparación de los experimentos reales, usando fixtures separados y conservando ambos registros.')
 b.page();b.h('5. Mensaje envenenado y DLQ')
 b.p('Se publicó un JSON malformado. Con el envío a la DLQ desactivado para demostrar el fallo, el consumidor lo reencolaba; se conservaron sus logs. Se detuvo el consumidor, se retiró únicamente ese mensaje conocido y se verificó que la cola estuviera vacía antes de recrearla con los argumentos de DLX.')
 b.p('Después, basicNack sin requeue mandó el mensaje al DLX transferencias.dlx y a notificacion.transferencias.dlq. La cabecera x-death registra reason: rejected. Una transferencia válida posterior produjo su aviso, por lo que el mensaje malo no bloqueó el procesamiento normal.')
 b.img('dlq.png','DLQ y mensaje rechazado, junto con evidencia del procesamiento válido posterior.')
 b.page();b.h('5. Panel de la cola de mensajes rechazados')
 b.img('rabbit-dlq.png','Panel real de RabbitMQ con el mensaje en la DLQ. La cabecera x-death y el rechazo se conservan en dlq.json.')
 b.h('Aviso de compensación')
 b.p('Se provocó una falla de abono con la saga de la base: respondió HTTP '+str(revertida['transferencia']['codigo'])+' y quedó '+revertida['transferencia']['cuerpo']['estado']+'. Ese código indica que la transferencia no se realizó; se verificaron ambos saldos conservados y el aviso de TransferenciaRevertida para origen. El registro completo es revertida.json.')
 b.img('revertida.png','Resultado real y aviso al origen de la transferencia compensada.')
 b.page();b.h('6. Preguntas de cierre')
 b.p('Broker caído: esta entrega no implementa el reto Outbox. El estado final puede persistir mientras el evento no sale; el publicador registra el fallo y se necesita recuperación. Publisher confirm no une la base con el broker ni prueba que el consumidor guardó un aviso.')
 b.p('Ack después de guardar: si el proceso cae entre el commit y el ack, el broker puede entregar otra vez. La clave única evita repetir los efectos. Confirmar antes permitiría perder un aviso si el proceso cae antes de guardarlo. No se promete entrega exactamente una vez en la red.')
 b.p('Elección: usar avisos asíncronos. Porque: un consumidor caído no debe bloquear la respuesta de una transferencia que ya movió el dinero. Sacrificio: la respuesta HTTP no certifica que el aviso ya esté en la bandeja; hace falta observar retrasos y fallas del consumidor.')
 b.h('Qué viene de la base y qué se añadió')
 b.p('La rama oficial 05-mensajeria-base ya contiene la saga, sus reintentos y el circuit breaker del profesor. Esta entrega añade publicación y consumo AMQP, validación del evento, idempotencia de avisos y DLQ. Las pruebas nuevas verifican el orden del ack, veneno y fallas transitorias. Se entregan completos transferencia y notificacion, sin target.')
 b.p('Fuentes: contrato oficial Lab S09; documentación de RabbitMQ sobre confirms y DLX; Spring AMQP sobre RabbitTemplate. La versión exacta del código base y su archivo descargado constan en evidencias/fuente.json. Los interruptores de demostración quedan activados para idempotencia y DLQ en la configuración normal.','PieImagen')
 b.finish()

{'s07':s07,'s08':s08,'s09':s09}[sys.argv[1]]()
