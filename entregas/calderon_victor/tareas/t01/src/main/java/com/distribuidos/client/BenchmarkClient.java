package com.distribuidos.client;

import com.distribuidos.grpc.BookProto;
import com.distribuidos.grpc.BookRequest;
import com.distribuidos.grpc.BookResponse;
import com.distribuidos.grpc.BookServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cliente automatizado de pruebas y benchmarking que consulta el servicio
 * mediante REST (HTTP/1.1 + JSON) y gRPC (HTTP/2 + Protobuf).
 *
 * Valida casos de éxito, casos de error y cuantifica la diferencia
 * en bytes de payload entre ambas tecnologías (Punto Extra).
 */
public class BenchmarkClient {

    private final String host;
    private final int restPort;
    private final int grpcPort;
    private final HttpClient httpClient;
    private final ManagedChannel channel;
    private final BookServiceGrpc.BookServiceBlockingStub blockingStub;

    public BenchmarkClient(String host, int restPort, int grpcPort) {
        this.host = host;
        this.restPort = restPort;
        this.grpcPort = grpcPort;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.channel = ManagedChannelBuilder.forAddress(host, grpcPort)
                .usePlaintext()
                .build();

        this.blockingStub = BookServiceGrpc.newBlockingStub(channel);
    }

    public void close() {
        try {
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("SERVER_HOST", "localhost");
        int restPort = Integer.parseInt(System.getenv().getOrDefault("REST_PORT", "8080"));
        int grpcPort = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));

        System.out.println("================================================================================");
        System.out.println("   BENCHMARK & CLIENTE DE VALIDACIÓN: REST vs gRPC");
        System.out.println("   Materia: Sistemas Distribuidos | Tarea T01");
        System.out.println("   Autor: Victor Emiliano Calderón Gutiérrez");
        System.out.println("   Objetivo: " + host + " (REST :" + restPort + " | gRPC :" + grpcPort + ")");
        System.out.println("================================================================================\n");

        BenchmarkClient client = new BenchmarkClient(host, restPort, grpcPort);

        try {
            boolean allPassed = client.runSuite();
            if (allPassed) {
                System.out.println("\n[RESULTADO GLOBAL] >>> TODAS LAS PRUEBAS PASARON EXITOSAMENTE (REST & gRPC) <<<");
                System.exit(0);
            } else {
                System.err.println("\n[RESULTADO GLOBAL] >>> HUBO FALLOS EN LAS PRUEBAS <<<");
                System.exit(1);
            }
        } finally {
            client.close();
        }
    }

    public boolean runSuite() {
        boolean restSuccessOk = testRestSuccess("ddia");
        boolean grpcSuccessOk = testGrpcSuccess("ddia");
        boolean restNotFoundOk = testRestNotFound("libro_inexistente_xyz");
        boolean grpcNotFoundOk = testGrpcNotFound("libro_inexistente_xyz");

        runDetailedBenchmark(List.of("ddia", "k8s", "hpbn", "sre"));

        return restSuccessOk && grpcSuccessOk && restNotFoundOk && grpcNotFoundOk;
    }

    // ---------------------------------------------------------------------------------------------
    // PRUEBAS DE ÉXITO (ID EXISTENTE)
    // ---------------------------------------------------------------------------------------------

    private boolean testRestSuccess(String id) {
        System.out.println("--- [TEST 1: REST ÉXITO - ID: " + id + "] ---");
        try {
            String uri = String.format("http://%s:%d/books/%s", host, restPort, id);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            byte[] rawBytes = response.body().getBytes(StandardCharsets.UTF_8);

            System.out.println("  URL Solicitada      : " + uri);
            System.out.println("  Código HTTP         : " + response.statusCode());
            System.out.println("  Cuerpo JSON Recibido: " + response.body());
            System.out.println("  Bytes reales de payload: " + rawBytes.length + " bytes");

            if (response.statusCode() == 200 && response.body().contains("Designing Data-Intensive Applications")) {
                System.out.println("  Estado: PASS [200 OK]");
                return true;
            } else {
                System.err.println("  Estado: FAIL (código o contenido inesperado)");
                return false;
            }
        } catch (Exception e) {
            System.err.println("  Error ejecutando solicitud REST: " + e.getMessage());
            return false;
        }
    }

    private boolean testGrpcSuccess(String id) {
        System.out.println("\n--- [TEST 2: gRPC ÉXITO - ID: " + id + "] ---");
        try {
            BookRequest request = BookRequest.newBuilder().setId(id).build();
            BookResponse response = blockingStub.getBook(request);
            int serializedSize = response.getSerializedSize();

            System.out.println("  Método gRPC         : BookService/GetBook");
            System.out.println("  Respuesta Protobuf  : {\n" + response.toString().trim() + "\n  }");
            System.out.println("  Bytes reales Protobuf: " + serializedSize + " bytes");

            if ("ddia".equals(response.getId()) && response.getTitle().contains("Designing Data-Intensive Applications")) {
                System.out.println("  Estado: PASS [OK]");
                return true;
            } else {
                System.err.println("  Estado: FAIL (campos de respuesta no coinciden)");
                return false;
            }
        } catch (Exception e) {
            System.err.println("  Error ejecutando RPC gRPC: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PRUEBAS DE ERROR (ID INEXISTENTE)
    // ---------------------------------------------------------------------------------------------

    private boolean testRestNotFound(String id) {
        System.out.println("\n--- [TEST 3: REST RECURSO NO ENCONTRADO - ID: " + id + "] ---");
        try {
            String uri = String.format("http://%s:%d/books/%s", host, restPort, id);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("  URL Solicitada      : " + uri);
            System.out.println("  Código HTTP         : " + response.statusCode());
            System.out.println("  Cuerpo JSON Recibido: " + response.body());

            if (response.statusCode() == 404 && response.body().contains("Libro no encontrado")) {
                System.out.println("  Estado: PASS [404 Not Found esperado con detalle]");
                return true;
            } else {
                System.err.println("  Estado: FAIL (se esperaba 404 con mensaje 'Libro no encontrado')");
                return false;
            }
        } catch (Exception e) {
            System.err.println("  Error ejecutando solicitud REST: " + e.getMessage());
            return false;
        }
    }

    private boolean testGrpcNotFound(String id) {
        System.out.println("\n--- [TEST 4: gRPC RECURSO NO ENCONTRADO - ID: " + id + "] ---");
        try {
            BookRequest request = BookRequest.newBuilder().setId(id).build();
            BookResponse response = blockingStub.getBook(request);
            System.err.println("  Estado: FAIL (Se esperaba excepción StatusRuntimeException NOT_FOUND pero retornó respuesta)");
            return false;
        } catch (StatusRuntimeException e) {
            System.out.println("  Código gRPC Capturado: " + e.getStatus().getCode());
            System.out.println("  Descripción del Error: " + e.getStatus().getDescription());

            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                System.out.println("  Estado: PASS [Status.NOT_FOUND capturado correctamente]");
                return true;
            } else {
                System.err.println("  Estado: FAIL (Código gRPC incorrecto: " + e.getStatus().getCode() + ")");
                return false;
            }
        } catch (Exception e) {
            System.err.println("  Error inesperado: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PUNTO EXTRA: COMPARATIVA DE BYTES REALES (PAYLOAD BENCHMARK)
    // ---------------------------------------------------------------------------------------------

    private void runDetailedBenchmark(List<String> bookIds) {
        System.out.println("\n================================================================================");
        System.out.println("   [PUNTO EXTRA] MEDICIÓN DE BYTES EN EL CUERPO: REST (JSON) vs gRPC (Protobuf)");
        System.out.println("================================================================================");
        System.out.println(String.format("%-10s | %-40s | %-12s | %-12s | %-10s",
                "ID", "Título", "REST (Bytes)", "gRPC (Bytes)", "Ahorro (%)"));
        System.out.println("--------------------------------------------------------------------------------");

        long totalRestBytes = 0;
        long totalGrpcBytes = 0;

        for (String id : bookIds) {
            try {
                // 1. REST
                String uri = String.format("http://%s:%d/books/%s", host, restPort, id);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
                HttpResponse<String> restResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                byte[] restBytes = restResp.body().getBytes(StandardCharsets.UTF_8);

                // 2. gRPC
                BookResponse grpcResp = blockingStub.getBook(BookRequest.newBuilder().setId(id).build());
                int grpcBytes = grpcResp.getSerializedSize();

                totalRestBytes += restBytes.length;
                totalGrpcBytes += grpcBytes;

                double savings = ((double) (restBytes.length - grpcBytes) / restBytes.length) * 100.0;
                String truncatedTitle = grpcResp.getTitle().length() > 38 ? grpcResp.getTitle().substring(0, 35) + "..." : grpcResp.getTitle();

                System.out.println(String.format("%-10s | %-40s | %12d | %12d | %9.2f%%",
                        id, truncatedTitle, restBytes.length, grpcBytes, savings));

            } catch (Exception e) {
                System.err.println("Error midiendo ID " + id + ": " + e.getMessage());
            }
        }

        System.out.println("--------------------------------------------------------------------------------");
        double totalSavings = ((double) (totalRestBytes - totalGrpcBytes) / totalRestBytes) * 100.0;
        System.out.println(String.format("%-10s | %-40s | %12d | %12d | %9.2f%%",
                "TOTAL", "Acumulado (" + bookIds.size() + " llamadas exitosas)", totalRestBytes, totalGrpcBytes, totalSavings));
        System.out.println("================================================================================\n");

        System.out.println("Análisis de Metodología de Medición:");
        System.out.println(" - REST: Se calculó la longitud en bytes del texto JSON UTF-8 crudo recibido en el cuerpo HTTP.");
        System.out.println(" - gRPC: Se calculó mediante getSerializedSize() del mensaje Protobuf binario deserializado.");
        System.out.println(" - Conclusión: gRPC reduce significativamente el tamaño del payload al omitir nombres de atributos");
        System.out.println("   repetidos, llaves, comillas y codificar enteros en formato varint compacto.");
    }
}
