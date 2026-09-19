from fastapi import FastAPI
from app.logic import get_student

app = FastAPI()


@app.get("/students/{student_id}")
def get_student_rest(student_id: int):
    student = get_student(student_id)

    if student is None:
        return {
            "message": "No hay datos para ese ID"
        }

    return student