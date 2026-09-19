import sys
import time

import requests
import grpc

from generated import service_pb2
from generated import service_pb2_grpc


def wait_for_rest():
    url = "http://server:8000/students/1"

    for attempt in range(15):
        try:
            requests.get(url, timeout=2)
            print("REST está disponible.")
            return
        except requests.exceptions.RequestException:
            print(f"Esperando REST... intento {attempt + 1}/15")
            time.sleep(2)

    raise Exception("REST no estuvo disponible.")


def wait_for_grpc():
    for attempt in range(15):
        try:
            channel = grpc.insecure_channel("server:50051")

            grpc.channel_ready_future(channel).result(timeout=2)

            print("gRPC está disponible.")
            channel.close()
            return

        except grpc.FutureTimeoutError:
            print(f"Esperando gRPC... intento {attempt + 1}/15")
            time.sleep(2)

    raise Exception("gRPC no estuvo disponible.")


def call_rest(student_id):
    url = f"http://server:8000/students/{student_id}"

    response = requests.get(url)

    print("========== REST ==========")
    print("Status:", response.status_code)
    print("Respuesta:")
    print(response.json())


def call_grpc(student_id):
    channel = grpc.insecure_channel("server:50051")

    stub = service_pb2_grpc.StudentServiceStub(channel)

    request = service_pb2.StudentRequest(id=student_id)

    response = stub.GetStudent(request)

    print("========== gRPC ==========")
    print("ID:", response.id)
    print("Nombre:", response.name)
    print("Edad:", response.age)
    print("Encontrado:", response.found)

    channel.close()


student_id = int(sys.argv[1]) if len(sys.argv) > 1 else 2

wait_for_rest()
wait_for_grpc()

call_rest(student_id)
call_grpc(student_id)

