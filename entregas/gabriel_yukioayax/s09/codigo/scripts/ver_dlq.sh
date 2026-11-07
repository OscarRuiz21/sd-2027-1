# Get messages de la DLQ (como en el panel, con "Nack message requeue true"): payload y cabecera x-death
curl -s -u mexibanco:mexibanco -H 'Content-Type: application/json' -X POST http://localhost:15673/api/queues/%2F/notificacion.transferencias.dlq/get -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto"}' | python -c "
import sys,json
for m in json.load(sys.stdin):
    print('payload        :', repr(m['payload']))
    print('routing_key    :', m['routing_key'], '| exchange:', m['exchange'], '| redelivered:', m['redelivered'])
    for d in m['properties']['headers'].get('x-death', []):
        print('x-death        :', {k: d[k] for k in ('reason', 'queue', 'exchange', 'routing-keys', 'count')})
"
