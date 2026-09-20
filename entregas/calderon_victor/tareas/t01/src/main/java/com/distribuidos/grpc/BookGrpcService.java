package com.distribuidos.grpc;

import com.distribuidos.domain.Book;
import com.distribuidos.domain.BookRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Servicio gRPC que expone el catálogo de libros mediante HTTP/2 y Protobuf binario.
 * Consume exactamente la misma fuente de verdad: BookRepository.
 */
@GrpcService
public class BookGrpcService extends BookServiceGrpc.BookServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BookGrpcService.class);
    private final BookRepository bookRepository;

    public BookGrpcService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * RPC para consultar un libro por su ID.
     * Si existe: Retorna BookResponse serializado en formato binario Protobuf.
     * Si no existe: Notifica error con código gRPC Status.NOT_FOUND.
     */
    @Override
    public void getBook(BookRequest request, StreamObserver<BookResponse> responseObserver) {
        String bookId = request.getId();
        log.info("[gRPC] Solicitud recibida: BookService/GetBook con ID '{}'", bookId);

        Optional<Book> bookOpt = bookRepository.findById(bookId);

        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            BookResponse response = BookResponse.newBuilder()
                    .setId(book.id())
                    .setTitle(book.title())
                    .setAuthor(book.author())
                    .setPages(book.pages())
                    .setYear(book.year())
                    .setSummary(book.summary())
                    .build();

            log.info("[gRPC] Retornando libro: '{}' [Tamaño Protobuf: {} bytes]",
                    book.title(), response.getSerializedSize());

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            log.warn("[gRPC] Libro no encontrado para ID '{}'. Emite Status.NOT_FOUND", bookId);
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Libro no encontrado: " + bookId)
                            .asRuntimeException()
            );
        }
    }
}
