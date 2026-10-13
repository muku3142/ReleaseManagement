package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.StatusResponse;
import com.evision.ragservice.application.StatusService;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final StatusService status;

    public StatusController(StatusService status) {
        this.status = status;
    }

    @GetMapping("/v1/status")
    public StatusResponse status(
            @Parameter(hidden = true) @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) UUID clientId) {
        return StatusResponse.from(status.status(clientId));
    }
}
