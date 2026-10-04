"""Cliente sin dependencias para comprobar un servidor ya levantado."""

import argparse
import concurrent.futures
import json
import threading
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:8083')
    parser.add_argument('--clave', default='demo-' + uuid.uuid4().hex)
    parser.add_argument('--solo-replay', action='store_true')
    args = parser.parse_args()
    payload = {'monto_centavos': 12500, 'moneda': 'MXN', 'referencia': 'pedido-001'}

    def request(changed=False):
        body = dict(payload, monto_centavos=999) if changed else payload
        req = urllib.request.Request(args.url + '/cobros', data=json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json',
                                              'Idempotency-Key': args.clave})
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.headers.get('Idempotency-Replayed'), response.read()

    def show(label, result):
        print(f'{label}: HTTP {result[0]}, Idempotency-Replayed={result[1]}')
        print(result[2].decode())

    print('Clave:', args.clave)
    if args.solo_replay:
        result = request()
        show('Reintento tras reinicio', result)
        assert result[:2] == (201, 'true'), result
        print('OK: el resultado persistió y fue reproducido.')
        return
    gate = threading.Barrier(2)
    def simultaneous(_):
        gate.wait(timeout=10)
        return request()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(simultaneous, range(2)))
    for number, result in enumerate(results, start=1):
        show(f'Solicitud simultánea {number}', result)
    assert all(result[0] == 201 for result in results), results
    assert sorted(result[1] for result in results) == ['false', 'true'], 'Usa una clave nueva.'
    assert results[0][2] == results[1][2], results
    replay = request()
    show('Reintento', replay)
    assert replay == (201, 'true', results[0][2]), replay
    conflict = request(changed=True)
    show('Misma clave con otro monto', conflict)
    assert conflict[0] == 409, conflict
    print('OK: dos solicitudes, mismo resultado; replay exacto y conflicto rechazado.')


if __name__ == '__main__':
    main()
