package com.evision.releasemgmt.domain.portfolio;

import java.util.List;

public class ClientDeploymentRejectedException extends RuntimeException {

    private final ClientDeploymentRejection reason;
    private final List<RejectedClient> clients;

    public ClientDeploymentRejectedException(ClientDeploymentRejection reason) {
        this(reason, List.of());
    }

    public ClientDeploymentRejectedException(ClientDeploymentRejection reason, List<RejectedClient> clients) {
        super(reason.name());
        this.reason = reason;
        this.clients = List.copyOf(clients);
    }

    public ClientDeploymentRejection reason() {
        return reason;
    }

    public List<RejectedClient> clients() {
        return clients;
    }
}
