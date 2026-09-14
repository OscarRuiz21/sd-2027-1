const db = { "1": "Juan Carlos ", "2": "ElIAs ", "3": "Ariadna" };

function getData(id){
    if (db[id]){
        return {success: true, message:"Dato encontado", data: db[id]};
    }
    return {success: false, message:"Dato NO encontrado", data: null};
}

module.exports = {getData};