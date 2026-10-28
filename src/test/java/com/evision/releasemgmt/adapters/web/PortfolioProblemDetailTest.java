package com.evision.releasemgmt.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.releasemgmt.domain.portfolio.ClientDeploymentNotFoundException;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejectedException;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejection;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.portfolio.RejectedClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class PortfolioProblemDetailTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void everyInstallRejectionMapsToItsOwnReasonCode() {
        for (ClientDeploymentRejection rejection : ClientDeploymentRejection.values()) {
            ProblemDetail pd = handler.handleInstallRejected(new ClientDeploymentRejectedException(rejection));
            assertThat(pd.getProperties()).containsEntry("reason", rejection.name());
            assertThat(pd.getStatus()).isIn(409, 422);
            assertThat(pd.getProperties()).doesNotContainKey("clients");
        }
    }

    @Test
    void everyPortfolioEditRejectionMapsToItsOwnReasonCode() {
        for (PortfolioEditRejection rejection : PortfolioEditRejection.values()) {
            ProblemDetail pd = handler.handlePortfolioEditRejected(new PortfolioEditRejectedException(rejection));
            assertThat(pd.getProperties()).containsEntry("reason", rejection.name());
        }
    }

    @Test
    void releaseProductRulesUseTheirDedicatedStatuses() {
        assertThat(handler.handlePortfolioEditRejected(
                new PortfolioEditRejectedException(PortfolioEditRejection.RELEASE_PRODUCT_REQUIRED)).getStatus()).isEqualTo(422);
        assertThat(handler.handlePortfolioEditRejected(
                new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NO_JIRA_PROJECT)).getStatus()).isEqualTo(422);
        assertThat(handler.handlePortfolioEditRejected(
                new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NO_JIRA_PROJECT)).getProperties())
                .containsEntry("reason", "PRODUCT_NO_JIRA_PROJECT");
    }

    @Test
    void rejectedClientsAreListedEvenWhenTheNameIsUnknown() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        ProblemDetail pd = handler.handleInstallRejected(new ClientDeploymentRejectedException(
                ClientDeploymentRejection.INSTALL_CLIENTS_REJECTED,
                List.of(new RejectedClient(known, "Acme", RejectedClient.Reason.ARCHIVED),
                        new RejectedClient(unknown, null, RejectedClient.Reason.NOT_LINKED))));

        assertThat(pd.getStatus()).isEqualTo(409);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) pd.getProperties().get("clients");
        assertThat(clients).hasSize(2);
        assertThat(clients.get(0)).containsEntry("clientId", known).containsEntry("name", "Acme")
                .containsEntry("reason", "ARCHIVED");
        assertThat(clients.get(1)).containsEntry("clientId", unknown).containsEntry("name", null)
                .containsEntry("reason", "NOT_LINKED");
    }

    @Test
    void notFoundExceptionsMapTo404WithStableReasons() {
        ProblemDetail product = handler.handleProductNotFound(new ProductNotFoundException());
        ProblemDetail client = handler.handleClientNotFound(new ClientNotFoundException());
        ProblemDetail install = handler.handleInstallNotFound(new ClientDeploymentNotFoundException());

        assertThat(product.getStatus()).isEqualTo(404);
        assertThat(product.getProperties()).containsEntry("reason", "PRODUCT_NOT_FOUND");
        assertThat(client.getStatus()).isEqualTo(404);
        assertThat(client.getProperties()).containsEntry("reason", "CLIENT_NOT_FOUND");
        assertThat(install.getStatus()).isEqualTo(404);
        assertThat(install.getProperties()).containsEntry("reason", "INSTALL_NOT_FOUND");
    }
}
