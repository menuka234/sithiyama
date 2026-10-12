package lk.sithiyama.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/** Shared response envelopes. */
public final class CommonDtos {

    private CommonDtos() {
    }

    /** A serialisable slice of a Spring Data {@link Page}. */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last) {

        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isLast());
        }

        public static <T> PageResponse<T> of(List<T> content, int page, int size) {
            return new PageResponse<>(content, page, size, content.size(), 1, true);
        }
    }

    /** Consistent error body for every non-2xx response. */
    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors) {

        public static ApiErrorResponse of(int status, String error, String message, String path) {
            return new ApiErrorResponse(Instant.now(), status, error, message, path, null);
        }

        public static ApiErrorResponse of(int status, String error, String message, String path,
                                          Map<String, String> fieldErrors) {
            return new ApiErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
        }
    }
}
