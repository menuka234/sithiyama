package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.dto.NodeDtos.NodeHeartbeatRequest;
import lk.sithiyama.backend.dto.NodeDtos.NodeRequest;
import lk.sithiyama.backend.dto.NodeDtos.NodeResponse;
import lk.sithiyama.backend.service.NodeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nodes")
@Tag(name = "Companion Nodes", description = "Register and monitor LoRa tracker nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a Companion Node (idempotent per owner)")
    public NodeResponse register(@Valid @RequestBody NodeRequest request) {
        return nodeService.register(request);
    }

    @GetMapping
    @Operation(summary = "List the nodes you own")
    public List<NodeResponse> listMine() {
        return nodeService.listMine();
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Report battery, firmware and last position")
    public NodeResponse heartbeat(@PathVariable UUID id, @Valid @RequestBody NodeHeartbeatRequest request) {
        return nodeService.heartbeat(id, request);
    }
}
