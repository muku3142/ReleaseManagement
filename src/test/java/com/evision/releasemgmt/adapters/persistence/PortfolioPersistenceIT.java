package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PortfolioPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ProductRepository products;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ReleaseProductRepository releaseProducts;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE product, client CASCADE");
    }

    private UUID release(String name, boolean freeze) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now().minus(1, ChronoUnit.HOURS));
        releaseRepository.create(release, List.of());
        if (freeze) {
            releaseRepository.freeze(release.id(), "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        }
        return release.id();
    }

    private Instant itemsChangedAt(UUID releaseId) {
        Timestamp ts = jdbc.queryForObject("SELECT items_changed_at FROM release WHERE id = ?", Timestamp.class, releaseId);
        return ts == null ? null : ts.toInstant();
    }

    private void install(UUID releaseId, UUID productId, UUID clientId, String outcome, UUID supersedes) {
        jdbc.update("""
                INSERT INTO client_deployment (id, release_id, product_id, client_id, outcome, installed_at, recorded_by,
                                               supersedes_id, correction_reason)
                VALUES (?, ?, ?, ?, ?, ?, 'acc-1', ?, ?)
                """, UUID.randomUUID(), releaseId, productId, clientId, outcome,
                Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), supersedes,
                supersedes == null ? null : "wrong");
    }

    private UUID installRow(UUID releaseId, UUID productId, UUID clientId) {
        install(releaseId, productId, clientId, "SUCCESS", null);
        return jdbc.queryForObject("SELECT id FROM client_deployment WHERE release_id = ? AND product_id = ? AND client_id = ?",
                UUID.class, releaseId, productId, clientId);
    }

    private void assertRejected(Runnable action, PortfolioEditRejection reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void productAndClientRegistersAddRenameArchiveAndRejectDuplicates() {
        Product core = products.add("Core");
        products.add("alpha");
        assertThat(products.findAll()).extracting(Product::name).containsExactly("alpha", "Core");
        assertRejected(() -> products.add("core"), PortfolioEditRejection.PRODUCT_NAME_DUPLICATE);
        assertRejected(() -> products.rename(core.id(), "ALPHA"), PortfolioEditRejection.PRODUCT_NAME_DUPLICATE);
        products.rename(core.id(), "Core2");
        products.setArchivedAt(core.id(), Instant.now());
        assertThat(products.findById(core.id())).get().matches(Product::archived);
        products.setArchivedAt(core.id(), null);
        assertThat(products.findById(core.id())).get().matches(p -> !p.archived());
        assertThatThrownBy(() -> products.rename(UUID.randomUUID(), "X")).isInstanceOf(ProductNotFoundException.class);

        Client acme = clients.add("Acme");
        clients.add("beta");
        assertThat(clients.findAll()).extracting(Client::name).containsExactly("Acme", "beta");
        assertRejected(() -> clients.add("ACME"), PortfolioEditRejection.CLIENT_NAME_DUPLICATE);
        assertRejected(() -> clients.rename(acme.id(), "Beta"), PortfolioEditRejection.CLIENT_NAME_DUPLICATE);
        clients.setArchivedAt(acme.id(), Instant.now());
        assertThat(clients.findById(acme.id())).get().matches(Client::archived);
        assertThatThrownBy(() -> clients.rename(UUID.randomUUID(), "X")).isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void linkingIsIdempotentAndRejectsArchivedSides() {
        Product p1 = products.add("P1");
        Product p2 = products.add("P2");
        Product archivedProduct = products.add("Old");
        products.setArchivedAt(archivedProduct.id(), Instant.now());
        Client c = clients.add("C");
        Client archivedClient = clients.add("Gone");
        clients.setArchivedAt(archivedClient.id(), Instant.now());

        clients.link(c.id(), p1.id());
        clients.link(c.id(), p1.id());
        clients.link(c.id(), p2.id());

        assertThat(clients.findById(c.id())).get().extracting(Client::productIds)
                .isEqualTo(Set.of(p1.id(), p2.id()));
        assertThat(clients.findAll().stream().filter(x -> x.id().equals(c.id())).findFirst().orElseThrow().productIds())
                .hasSize(2);
        assertRejected(() -> clients.link(archivedClient.id(), p1.id()), PortfolioEditRejection.CLIENT_ARCHIVED);
        assertRejected(() -> clients.link(c.id(), archivedProduct.id()), PortfolioEditRejection.PRODUCT_ARCHIVED);
        assertThat(products.clientCounts()).containsEntry(p1.id(), 1L).containsEntry(p2.id(), 1L);
    }

    @Test
    void unlinkIsBlockedByAnyInstallRowIncludingVoidedOnes() {
        Product p = products.add("P");
        Product other = products.add("Q");
        Client c = clients.add("C");
        clients.link(c.id(), p.id());
        clients.link(c.id(), other.id());
        clients.unlink(c.id(), other.id());
        assertThat(clients.findById(c.id())).get().extracting(Client::productIds).isEqualTo(Set.of(p.id()));

        UUID r = release("R", false);
        releaseProducts.replace(r, Set.of(p.id()));
        releaseRepository.freeze(r, "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        UUID original = installRow(r, p.id(), c.id());
        assertRejected(() -> clients.unlink(c.id(), p.id()), PortfolioEditRejection.CLIENT_LINK_HAS_INSTALLS);

        install(r, p.id(), c.id(), "VOIDED", original);
        assertRejected(() -> clients.unlink(c.id(), p.id()), PortfolioEditRejection.CLIENT_LINK_HAS_INSTALLS);
    }

    @Test
    void replaceOnOpenReleaseStampsOnlyWhenTheSetChanges() {
        Product a = products.add("A");
        Product b = products.add("B");
        UUID r = release("R", false);

        releaseProducts.replace(r, Set.of(a.id(), b.id()));

        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::name).containsExactly("A", "B");
        Instant stamped = itemsChangedAt(r);
        assertThat(stamped).isNotNull();

        releaseProducts.replace(r, Set.of(a.id(), b.id()));
        assertThat(itemsChangedAt(r)).isEqualTo(stamped);
    }

    @Test
    void replaceOnFrozenReleaseSucceedsAndStamps() {
        Product a = products.add("A");
        UUID r = release("R", true);

        releaseProducts.replace(r, Set.of(a.id()));

        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::id).containsExactly(a.id());
        assertThat(itemsChangedAt(r)).isNotNull();
    }

    @Test
    void replaceRejectsArchivedAdditionsAndRemovalOfInstalledProducts() {
        Product a = products.add("A");
        Product b = products.add("B");
        Product c2 = products.add("C");
        Product archived = products.add("Old");
        Client client = clients.add("Client");
        clients.link(client.id(), a.id());
        clients.link(client.id(), b.id());
        UUID r = release("R", false);
        releaseProducts.replace(r, Set.of(a.id(), b.id(), c2.id()));
        releaseRepository.freeze(r, "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        products.setArchivedAt(archived.id(), Instant.now());
        Instant before = itemsChangedAt(r);

        assertRejected(() -> releaseProducts.replace(r, Set.of(a.id(), b.id(), c2.id(), archived.id())),
                PortfolioEditRejection.PRODUCT_ARCHIVED);

        UUID original = installRow(r, a.id(), client.id());
        install(r, a.id(), client.id(), "VOIDED", original);
        assertRejected(() -> releaseProducts.replace(r, Set.of(b.id(), c2.id())),
                PortfolioEditRejection.PRODUCT_HAS_INSTALLS_ON_RELEASE);
        assertThat(releaseProducts.productsOf(r)).hasSize(3);
        assertThat(itemsChangedAt(r)).isEqualTo(before);

        releaseProducts.replace(r, Set.of(a.id(), b.id()));
        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::name).containsExactly("A", "B");
    }

    @Test
    void productsOfReportsInstallsAndOmitsReleasesWithoutProducts() {
        Product a = products.add("A");
        Product b = products.add("B");
        Client client = clients.add("Client");
        clients.link(client.id(), a.id());
        UUID r = release("R", false);
        releaseProducts.replace(r, Set.of(a.id(), b.id()));
        releaseRepository.freeze(r, "rm@acme.com", Instant.now().minus(30, ChronoUnit.MINUTES));
        installRow(r, a.id(), client.id());
        UUID empty = release("Empty", false);

        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::hasInstalls).containsExactly(true, false);
        assertThat(releaseProducts.productsOfReleases(List.of(r, empty))).containsOnlyKeys(r);
        assertThat(releaseProducts.productsOfReleases(List.of())).isEmpty();
        assertThat(products.releaseCounts()).containsEntry(a.id(), 1L);
    }

    @Test
    void aReleaseNeverEndsUpWithoutAProduct() {
        Product a = products.add("A");
        Product b = products.add("B");
        UUID r = release("R", false);
        releaseProducts.replace(r, Set.of(a.id()));
        Instant before = itemsChangedAt(r);

        assertRejected(() -> releaseProducts.replace(r, Set.of()), PortfolioEditRejection.RELEASE_LAST_PRODUCT);
        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::id).containsExactly(a.id());
        assertThat(itemsChangedAt(r)).isEqualTo(before);

        UUID bare = release("Bare", false);
        assertRejected(() -> releaseProducts.replace(bare, Set.of()), PortfolioEditRejection.RELEASE_LAST_PRODUCT);

        releaseProducts.replace(r, Set.of(b.id()));
        assertThat(releaseProducts.productsOf(r)).extracting(ReleaseProduct::id).containsExactly(b.id());
    }
}
