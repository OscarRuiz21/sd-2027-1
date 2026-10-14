class ArticuloNoEncontrado(Exception):

    def __init__(self, id_articulo: str):
        self.id_articulo = id_articulo
        super().__init__(f"No hay datos para el ID '{id_articulo}'")


_CATALOGO: dict[str, dict] = {
    "A-100": {
        "id": "A-100",
        "nombre": "Teclado mecanico 87 teclas",
        "categoria": "Perifericos",
        "precio": 1299.50,
        "existencias": 14,
    },
    "A-101": {
        "id": "A-101",
        "nombre": "Mouse optico inalambrico",
        "categoria": "Perifericos",
        "precio": 449.00,
        "existencias": 63,
    },
    "B-200": {
        "id": "B-200",
        "nombre": "Monitor IPS 27 pulgadas",
        "categoria": "Pantallas",
        "precio": 5890.00,
        "existencias": 7,
    },
    "C-300": {
        "id": "C-300",
        "nombre": "SSD NVMe 1 TB",
        "categoria": "Almacenamiento",
        "precio": 1750.25,
        "existencias": 0,
    },
}


def normalizar_id(id_articulo: str) -> str:
    return (id_articulo or "").strip().upper()


def consultar_articulo(id_articulo: str) -> dict:
    clave = normalizar_id(id_articulo)
    if clave not in _CATALOGO:
        raise ArticuloNoEncontrado(clave)
    return dict(_CATALOGO[clave])


def listar_ids() -> list[str]:
    return sorted(_CATALOGO.keys())
