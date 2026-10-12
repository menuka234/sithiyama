package lk.sithiyama.backend.exception;

import java.util.UUID;

/** Thrown when a requested resource does not exist or is not visible to the caller. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String type, UUID id) {
        return new ResourceNotFoundException("%s %s was not found".formatted(type, id));
    }
}
