package com.evision.ragservice.adapters.web;

import com.evision.ragservice.adapters.web.dto.ClientResponse;
import com.evision.ragservice.adapters.web.dto.CreateClientRequest;
import com.evision.ragservice.adapters.web.dto.IssuedKeyResponse;
import com.evision.ragservice.application.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/clients")
public class AdminController {

    private final ClientService clients;

    public AdminController(ClientService clients) {
        this.clients = clients;
    }

    @PostMapping
    public ResponseEntity<IssuedKeyResponse> create(@Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(IssuedKeyResponse.from(clients.register(request.name())));
    }

    @PostMapping("/{id}/rotate")
    public IssuedKeyResponse rotate(@PathVariable UUID id) {
        return IssuedKeyResponse.from(clients.rotate(id));
    }

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        clients.revoke(id);
    }

    @GetMapping
    public List<ClientResponse> list() {
        return clients.list().stream().map(ClientResponse::from).toList();
    }
}
