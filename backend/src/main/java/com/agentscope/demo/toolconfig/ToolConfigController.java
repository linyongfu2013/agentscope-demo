package com.agentscope.demo.toolconfig;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool-configs")
public class ToolConfigController {

    private final ToolConfigService service;

    public ToolConfigController(ToolConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolConfigResponse> list(@RequestParam(name = "type", required = false) ToolType type) {
        return service.list(type);
    }

    @PostMapping
    public ToolConfigResponse create(@Valid @RequestBody ToolConfigRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ToolConfigResponse update(@PathVariable UUID id, @Valid @RequestBody ToolConfigRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test")
    public ToolTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }
}
