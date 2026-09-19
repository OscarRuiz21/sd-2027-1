import concurrent.futures
import threading
import grpc
import uvicorn
import service_pb2_grpc
from grpc_app import UserServiceServicer
from rest_app import app as rest_app

def start_grpc():
    server = grpc.server(concurrent.futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_UserServiceServicer_to_server(UserServiceServicer(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    server.wait_for_termination()

def start_rest():
    uvicorn.run(rest_app, host="0.0.0.0", port=8000)

if __name__ == "__main__":
    grpc_thread = threading.Thread(target=start_grpc, daemon=True)
    grpc_thread.start()
    start_rest()