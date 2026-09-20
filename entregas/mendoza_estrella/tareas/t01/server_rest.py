import django
from django.conf import settings
from django.http import JsonResponse
from django.urls import path

from logica import buscar_por_id

settings.configure(
    DEBUG=True,
    ALLOWED_HOSTS=["*"],
    ROOT_URLCONF=__name__,
    SECRET_KEY="uwu",
)
django.setup()


def vista_items(request, item_id):
    resultado = buscar_por_id(item_id)
    status = 200 if resultado["encontrado"] else 404
    return JsonResponse(resultado, status=status)


urlpatterns = [
    path("items/<str:item_id>/", vista_items),
]

if __name__ == "__main__":
    from django.core.management import execute_from_command_line
    import sys
    execute_from_command_line(sys.argv)