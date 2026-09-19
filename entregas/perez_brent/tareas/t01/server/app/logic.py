students = {
    1: {"name": "Christian", "age": 26},
    2: {"name": "Liz", "age": 22},
    3: {"name": "Diego", "age": 24}
}


def get_student(student_id):
    student = students.get(student_id)

    if student is None:
        return None

    return {
        "id": student_id,
        "name": student["name"],
        "age": student["age"]
    }