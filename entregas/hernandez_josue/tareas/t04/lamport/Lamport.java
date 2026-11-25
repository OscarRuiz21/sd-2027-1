public class Lamport {
    record Mensaje(String origen, String contenido, long reloj) {}

    static class Servidor {
        final String nombre;
        long reloj;

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        void eventoLocal(String accion) {
            reloj++;
            imprimir("local " + accion);
        }

        Mensaje enviar(String destino, String contenido) {
            reloj++;
            imprimir("envía a " + destino + " " + contenido);
            return new Mensaje(nombre, contenido, reloj);
        }

        void recibir(Mensaje mensaje) {
            reloj = Math.max(reloj, mensaje.reloj()) + 1;
            imprimir("recibe de " + mensaje.origen() + " " + mensaje.contenido()
                    + " (enviado en " + mensaje.reloj() + ")");
        }

        private void imprimir(String evento) {
            System.out.printf("%s %d | %s%n", nombre, reloj, evento);
        }
    }

    public static void main(String[] args) {
        Servidor a = new Servidor("A");
        Servidor b = new Servidor("B");
        Servidor c = new Servidor("C");

        a.eventoLocal("prepara el pedido");
        Mensaje pedido = a.enviar("B", "pedido");
        c.eventoLocal("revisa inventario");
        b.recibir(pedido);

        Mensaje reserva = b.enviar("C", "reserva");
        c.recibir(reserva);

        Mensaje confirmacion = c.enviar("A", "confirmación");
        a.recibir(confirmacion);

        Mensaje cierre = a.enviar("B", "cierre");
        b.recibir(cierre);
    }
}
