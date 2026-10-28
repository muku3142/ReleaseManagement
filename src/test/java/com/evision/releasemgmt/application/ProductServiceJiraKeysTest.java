package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductJiraKeyTakenException;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceJiraKeysTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository, Clock.systemUTC());
    private final Product alpha = new Product(UUID.randomUUID(), "Alpha", null);
    private final Product beta = new Product(UUID.randomUUID(), "Beta", null);

    @Test
    void keysAreStrippedUpperCasedDeduplicatedAndSorted() {
        when(repository.findById(alpha.id())).thenReturn(Optional.of(alpha));
        when(repository.productIdOwningJiraKey(any())).thenReturn(Optional.empty());

        service.setJiraProjectKeys(alpha.id(), List.of(" cnv ", "CNV", "abc"));

        verify(repository).setJiraProjectKeys(alpha.id(), List.of("ABC", "CNV"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"C", "1AB", "AB-C", "ABCDEFGHIJK", ""})
    void malformedKeysAreRejectedBeforeAnySave(String key) {
        when(repository.findById(alpha.id())).thenReturn(Optional.of(alpha));

        assertThatThrownBy(() -> service.setJiraProjectKeys(alpha.id(), List.of(key)))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_JIRA_KEY_INVALID));
        verify(repository, never()).setJiraProjectKeys(any(), any());
    }

    @Test
    void archivedProductIsRejected() {
        Product archived = new Product(UUID.randomUUID(), "Old", Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findById(archived.id())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.setJiraProjectKeys(archived.id(), List.of("CNV")))
                .isInstanceOfSatisfying(PortfolioEditRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PortfolioEditRejection.PRODUCT_ARCHIVED));
    }

    @Test
    void unknownProductIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setJiraProjectKeys(unknown, List.of("CNV")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void aKeyOwnedByAnotherProductNamesTheOwner() {
        when(repository.findById(alpha.id())).thenReturn(Optional.of(alpha));
        when(repository.findById(beta.id())).thenReturn(Optional.of(beta));
        when(repository.productIdOwningJiraKey("CNV")).thenReturn(Optional.of(beta.id()));

        assertThatThrownBy(() -> service.setJiraProjectKeys(alpha.id(), List.of("cnv")))
                .isInstanceOfSatisfying(ProductJiraKeyTakenException.class, e -> {
                    assertThat(e.key()).isEqualTo("CNV");
                    assertThat(e.productName()).isEqualTo("Beta");
                });
        verify(repository, never()).setJiraProjectKeys(any(), any());
    }

    @Test
    void aKeyAlreadyOwnedByTheSameProductIsFine() {
        when(repository.findById(alpha.id())).thenReturn(Optional.of(alpha));
        when(repository.productIdOwningJiraKey("CNV")).thenReturn(Optional.of(alpha.id()));

        service.setJiraProjectKeys(alpha.id(), List.of("CNV"));

        verify(repository).setJiraProjectKeys(alpha.id(), List.of("CNV"));
    }
}
