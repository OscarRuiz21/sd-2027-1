import grpc
import service_pb2
import service_pb2_grpc
from service import find_user_by_id

class UserServiceServicer(service_pb2_grpc.UserServiceServicer):
    def GetUser(self, request, context):
        user = find_user_by_id(request.id)
        if not user:
            return service_pb2.UserResponse(found=False)
        return service_pb2.UserResponse(
            found=True,
            id=user["id"],
            name=user["name"],
            email=user["email"]
        )