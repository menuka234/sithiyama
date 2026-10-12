package lk.sithiyama.backend.exception;

/** Thrown for requests that are syntactically valid but semantically wrong. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
