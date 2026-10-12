package lk.sithiyama.backend.exception;

/** Thrown when a unique value (username, email, device code) is already taken. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
