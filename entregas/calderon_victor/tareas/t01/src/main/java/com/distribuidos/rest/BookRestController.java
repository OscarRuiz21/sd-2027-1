package com.distribuidos.rest;

import com.distribuidos.domain.Book;
import com.distribuidos.domain.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST que expone el catálogo de libros mediante HTTP/1.1 y JSON.
 * Consume la misma fuente de verdad: BookRepository.
 */
@RestController
@RequestMapping("/books")
public class BookRestController {

    private static final Logger log = LoggerFactory.getLogger(BookRestController.class);
    private final BookRepository bookRepository;

    public BookRestController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * Endpoint para consultar un libro específico por su identificador.
     * GET /books/{id}
     *
     * Si existe: Retorna HTTP 200 OK con el objeto JSON del libro.
     * Si no existe: Retorna HTTP 404 Not Found con {"detail": "Libro no encontrado"}.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getBookById(@PathVariable("id") String id) {
        log.info("[REST] Solicitud recibida: GET /books/{}", id);
        Optional<Book> bookOpt = bookRepository.findById(id);

        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            log.info("[REST] Retornando libro: {}", book.title());
            return ResponseEntity.ok(book);
        } else {
            log.warn("[REST] Libro no encontrado para ID: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", "Libro no encontrado"));
        }
    }

    /**
     * Endpoint complementario para listar todos los libros.
     * GET /books
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Book>> getAllBooks() {
        log.info("[REST] Solicitud recibida: GET /books (listar todos)");
        return ResponseEntity.ok(bookRepository.findAll());
    }
}
