## Evidencia de Ejecución

**1. Consumo del servicio por REST y gRPC**
```bash
$ docker compose exec -it cliente sh
# python cliente.py rest 100
Llamando REST: http://servidor:8000/info/100
Respuesta REST: {'id': '100', 'datos': 'Emiliano Salgado - Ing. Computación UNAM', 'error': ''}

# python cliente.py grpc 100
Llamando gRPC a servidor:50051 con ID 100
Respuesta gRPC: {'id': '100', 'datos': 'Emiliano Salgado - Ing. Computación UNAM', 'error': ''}

**2. Captura de tráfico (Punto Extra)**

# Iniciar tcpdump para REST
# tcpdump -i eth0 port 8000 -w rest.pcap &
# tcpdump: listening on eth0, link-type EN10MB (Ethernet), snapshot length 262144 bytes
# python cliente.py rest 100
Llamando REST: http://servidor:8000/info/100
Respuesta REST: {'id': '100', 'datos': 'Emiliano Salgado - Ing. Computación UNAM', 'error': ''}

# Iniciar tcpdump para gRPC
# tcpdump -i eth0 port 50051 -w grpc.pcap &
# tcpdump: listening on eth0, link-type EN10MB (Ethernet), snapshot length 262144 bytes
# python cliente.py grpc 100
Llamando gRPC a servidor:50051 con ID 100
Respuesta gRPC: {'id': '100', 'datos': 'Emiliano Salgado - Ing. Computación UNAM', 'error': ''}

# Detener captura y medir bytes
# pkill tcpdump
12 packets captured
18 packets received by filter
0 packets dropped by kernel

# ls -l *.pcap
-rw-r--r-- 1 tcpdump tcpdump 2201 Sep 20 02:55 grpc.pcap
-rw-r--r-- 1 tcpdump tcpdump 1376 Sep 20 02:55 rest.pcap