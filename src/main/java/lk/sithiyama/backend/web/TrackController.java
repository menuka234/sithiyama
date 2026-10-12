package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.TrackDtos.TrackResponse;
import lk.sithiyama.backend.dto.TrackDtos.TrackSummary;
import lk.sithiyama.backend.dto.TrackDtos.TrackUploadRequest;
import lk.sithiyama.backend.service.TrackService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracks")
@Tag(name = "Track sync", description = "Upload recorded tracks and node breadcrumb logs")
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a recorded track (app recording or node breadcrumbs)")
    public TrackResponse upload(@Valid @RequestBody TrackUploadRequest request) {
        return trackService.upload(request);
    }

    @GetMapping
    @Operation(summary = "List the tracks you recorded")
    public PageResponse<TrackSummary> listMine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return trackService.listMine(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of your tracks with its full breadcrumb list")
    public TrackResponse get(@PathVariable UUID id) {
        return trackService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of your tracks")
    public void delete(@PathVariable UUID id) {
        trackService.delete(id);
    }
}
