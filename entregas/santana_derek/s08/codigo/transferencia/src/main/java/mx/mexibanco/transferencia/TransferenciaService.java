package mx.mexibanco.transferencia;
import mx.mexibanco.clientes.*;
import mx.mexibanco.compartido.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Saga S08 de Derek sobre la base S07; transacción local por paso. */
@Service
public class TransferenciaService {
 private static final Logger log=LoggerFactory.getLogger(TransferenciaService.class);
 private final TransferenciaRepository transferencias;
 private final CuentaCliente cuentas;
 private final MovimientoCliente movimientos;
 private final NotificacionCliente notificaciones;
 private final long pausa;
 private final String fallarAbono;
 public TransferenciaService(TransferenciaRepository transferencias,CuentaCliente cuentas,
  MovimientoCliente movimientos,NotificacionCliente notificaciones,
  @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausa,
  @Value("${FALLAR_ABONO_A_CLABE:}") String fallarAbono) {
  this.transferencias=transferencias;this.cuentas=cuentas;this.movimientos=movimientos;
  this.notificaciones=notificaciones;this.pausa=pausa;this.fallarAbono=fallarAbono;
 }
 public Transferencia transferir(String origen,String destino,BigDecimal monto) {
  if(origen.equals(destino)) throw new ReglaDeNegocioException("Las cuentas deben ser distintas");
  cuentas.consultar(origen);cuentas.consultar(destino);
  Transferencia t=transferencias.save(new Transferencia(origen,destino,monto));
  CuentaRemota cargo;
  try { cargo=cuentas.cargar(origen,monto); }
  catch(RuntimeException e) {
   if(!sinEfectoConfirmado(e)) return estado(t,"CARGO_INCIERTO","Conciliar antes de repetir: "+e.getMessage());
   estado(t,"RECHAZADA",e.getMessage());throw e;
  }
  t=estado(t,"CARGADA",null);
  if(pausa>0) {
   try {Thread.sleep(pausa);}
   catch(InterruptedException e){Thread.currentThread().interrupt();return estado(t,"COMPENSACION_PENDIENTE","Interrumpida; conciliar antes de reintentar");}
  }
  CuentaRemota abono;
  try {
   if(destino.equals(fallarAbono)) throw new ErrorRemotoException("cuenta",503,"Falla provocada ANTES de enviar el abono");
   abono=cuentas.abonar(destino,monto);
  } catch(RuntimeException e) {
   if(!sinEfectoConfirmado(e)) return estado(t,"ABONO_INCIERTO","Conciliar el abono antes de compensar: "+e.getMessage());
   return compensar(t,cargo,e.getMessage());
  }
  t=estado(t,"ABONADA",null);
  try {
   MovimientoRemoto c=movimientos.registrar(origen,TipoMovimiento.TRANSFERENCIA_ENVIADA,monto.negate(),cargo.saldo(),"a "+destino);
   MovimientoRemoto a=movimientos.registrar(destino,TipoMovimiento.TRANSFERENCIA_RECIBIDA,monto,abono.saldo(),"de "+origen);
   notificaciones.notificar(c);notificaciones.notificar(a);
   return estado(t,"COMPLETADA",null);
  } catch(RuntimeException e) {
   return estado(t,"ABONADA","Dinero movido; registro o aviso pendiente: "+e.getMessage());
  }
 }
 private Transferencia compensar(Transferencia t,CuentaRemota cargo,String motivo) {
  // Cuenta S07 no admite una clave de operación: no se programa reintento automático.
  t=estado(t,"COMPENSANDO",motivo);
  CuentaRemota devuelta;
  try {devuelta=cuentas.abonar(t.getClaveOrigen(),t.getMonto());}
  catch(RuntimeException e) {
   return estado(t,"COMPENSACION_PENDIENTE",motivo+"; conciliar antes de reintentar devolución: "+e.getMessage());
  }
  t=estado(t,"COMPENSADA_REGISTRO_PENDIENTE",motivo);
  try {
   movimientos.registrar(t.getClaveOrigen(),TipoMovimiento.TRANSFERENCIA_ENVIADA,t.getMonto().negate(),cargo.saldo(),"cargo saga "+t.getId());
   movimientos.registrar(t.getClaveOrigen(),TipoMovimiento.DEPOSITO,t.getMonto(),devuelta.saldo(),"DEVOLUCION saga "+t.getId());
   return estado(t,"COMPENSADA",motivo);
  } catch(RuntimeException e) {
   return estado(t,"COMPENSADA_REGISTRO_PENDIENTE","Saldo devuelto; registrar sin volver a abonar: "+e.getMessage());
  }
 }
 private boolean sinEfectoConfirmado(Throwable e) {
  if(e instanceof ErrorRemotoException remoto) {
   if(remoto.getEstado()<500) return true;
   String m=remoto.getMessage();
   return m.contains("Falla provocada") || m.contains("Circuito abierto");
  }
  for(Throwable p=e;p!=null;p=p.getCause()) {
   if(p instanceof java.net.ConnectException || p instanceof java.net.UnknownHostException) return true;
   // BlockingLoadBalancerClient rechaza aquí ANTES de enviar HTTP, sin instancia elegida.
   if(p instanceof IllegalStateException && "No instances available for cuenta".equals(p.getMessage())) return true;
  }
  // Un timeout, un reset de conexión o un 5xx genérico no prueban que no se escribió.
  return false;
 }
 private Transferencia estado(Transferencia t,String nuevo,String detalle) {
  t.cambiarEstado(nuevo,detalle);Transferencia saved=transferencias.save(t);
  log.info("Saga {} -> {}",saved.getId(),nuevo);return saved;
 }
 @Transactional(readOnly=true)
 public Transferencia consultar(Long id) {
  return transferencias.findById(id).orElseThrow(()->new NoEncontradoException("No existe la transferencia "+id));
 }
}
