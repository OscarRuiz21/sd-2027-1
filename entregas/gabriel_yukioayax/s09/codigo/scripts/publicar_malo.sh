# Publica {"hola":  en el exchange transferencias con la routing key transferencia.realizada,
# por la API HTTP del panel de RabbitMQ (lo mismo que el formulario "Publish message").
curl -s -u mexibanco:mexibanco -H 'Content-Type: application/json' -X POST http://localhost:15673/api/exchanges/%2F/transferencias/publish -d '{"properties":{},"routing_key":"transferencia.realizada","payload":"{\"hola\": ","payload_encoding":"string"}'
echo
