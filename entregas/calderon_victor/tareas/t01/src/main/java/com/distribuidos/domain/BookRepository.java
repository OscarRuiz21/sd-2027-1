package com.distribuidos.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio en memoria que actúa como la ÚNICA FUENTE DE VERDAD (Single Source of Truth)
 * para el catálogo de libros y la lógica de búsqueda y validación del negocio.
 *
 * Ambos controladores (REST y gRPC) inyectan este mismo componente.
 */
@Repository
public class BookRepository {

    private static final Logger log = LoggerFactory.getLogger(BookRepository.class);
    private final Map<String, Book> catalog = new ConcurrentHashMap<>();

    public BookRepository() {
        initCatalog();
    }

    private void initCatalog() {
        saveBook(new Book(
                "ddia",
                "Designing Data-Intensive Applications",
                "Martin Kleppmann",
                616,
                2017,
                "The definitive guide to the architecture, storage engines, distributed consensus, and fault tolerance in modern data systems."
        ));

        saveBook(new Book(
                "k8s",
                "Kubernetes: Up and Running",
                "Brendan Burns, Joe Beda, Kelsey Hightower",
                290,
                2022,
                "Dive into container orchestration, distributed deployment patterns, declarative APIs, and operational velocity with Kubernetes."
        ));

        saveBook(new Book(
                "hpbn",
                "High Performance Browser Networking",
                "Ilya Grigorik",
                400,
                2013,
                "Essential knowledge on TCP, UDP, TLS, HTTP/2, mobile network constraints, and latency optimization for modern web architectures."
        ));

        saveBook(new Book(
                "sre",
                "Site Reliability Engineering",
                "Betsy Beyer, Chris Jones, Jennifer Petoff, Niall Richard Murphy",
                550,
                2016,
                "How Google runs production systems at planetary scale with engineering-first reliability, error budgets, and distributed automation."
        ));

        log.info("BookRepository inicializado exitosamente con {} libros en memoria.", catalog.size());
    }

    private void saveBook(Book book) {
        catalog.put(book.id().toLowerCase(Locale.ROOT), book);
    }

    /**
     * Consulta un libro por su identificador.
     * Centraliza la validación de entrada y la recuperación del dominio.
     *
     * @param id Identificador del libro (ej: "ddia", "k8s", "hpbn")
     * @return Optional con el libro si fue encontrado, o vacío en caso contrario.
     */
    public Optional<Book> findById(String id) {
        if (id == null || id.isBlank()) {
            log.warn("Consulta rechazada: identificador nulo o en blanco.");
            return Optional.empty();
        }

        String normalizedId = id.trim().toLowerCase(Locale.ROOT);
        Optional<Book> result = Optional.ofNullable(catalog.get(normalizedId));

        if (result.isPresent()) {
            log.info("Libro encontrado con ID '{}': {}", normalizedId, result.get().title());
        } else {
            log.warn("Libro con ID '{}' no fue encontrado en el catálogo.", normalizedId);
        }

        return result;
    }

    /**
     * Retorna todos los libros del catálogo.
     */
    public List<Book> findAll() {
        return List.copyOf(catalog.values());
    }

    /**
     * Retorna la cantidad total de libros registrados.
     */
    public int count() {
        return catalog.size();
    }
}
