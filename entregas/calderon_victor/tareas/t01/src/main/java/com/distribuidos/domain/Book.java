package com.distribuidos.domain;

/**
 * Entidad de dominio que representa un libro técnico del catálogo de O'Reilly.
 * Utiliza un Record de Java 21 para inmutabilidad y serialización transparente.
 */
public record Book(
        String id,
        String title,
        String author,
        int pages,
        int year,
        String summary
) {
    public Book {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID del libro no puede ser nulo o vacío.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título no puede ser nulo o vacío.");
        }
    }
}
