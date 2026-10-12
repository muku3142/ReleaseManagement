package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredentialState;
import com.evision.releasemgmt.domain.ado.AdoCredentialStatus;
import com.evision.releasemgmt.domain.ado.AdoCredentialTokenRequiredException;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import com.evision.releasemgmt.ports.AdoCredentialRepository;
import com.evision.releasemgmt.ports.AdoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoCredentialServiceTest {

    private final AdoCredentialRepository repository = mock(AdoCredentialRepository.class);
    private final AdoRepositoryPort port = mock(AdoRepositoryPort.class);
    private final AdoCredentialService service = new AdoCredentialService(repository, port);
    private final LocalDate expiry = LocalDate.now(ZoneOffset.UTC).plusDays(90);

    @Test
    void validatesBeforeSaving() {
        when(repository.findStatus()).thenReturn(Optional.empty());

        service.save("acme", "pat", expiry, "rm");

        InOrder order = inOrder(port, repository);
        order.verify(port).validate("acme", "pat");
        order.verify(repository).save("acme", "pat", expiry, "rm");
    }

    @Test
    void rejectedTokenWritesNothing() {
        doThrow(new AdoAccessException(AdoFailureReason.UNAUTHORIZED)).when(port).validate("acme", "pat");

        assertThatThrownBy(() -> service.save("acme", "pat", expiry, "rm")).isInstanceOf(AdoAccessException.class);

        verify(repository, never()).save(any(), any(), any(), any());
    }

    @Test
    void blankTokenOnlyUpdatesExpiryAndNeverValidates() {
        when(repository.updateExpiry("acme", expiry, "rm")).thenReturn(true);
        when(repository.findStatus()).thenReturn(Optional.empty());

        service.save("acme", " ", expiry, "rm");

        verify(port, never()).validate(any(), any());
        verify(repository, never()).save(any(), any(), any(), any());
    }

    @Test
    void expiryOnlyWithoutStoredCredentialRequiresToken() {
        when(repository.updateExpiry("acme", expiry, "rm")).thenReturn(false);

        assertThatThrownBy(() -> service.save("acme", null, expiry, "rm"))
                .isInstanceOf(AdoCredentialTokenRequiredException.class);
    }

    @Test
    void statusIsExpiringFourteenDaysOut() {
        LocalDate in14 = LocalDate.now(ZoneOffset.UTC).plusDays(14);
        when(repository.findStatus()).thenReturn(Optional.of(new AdoCredentialStatus("acme", in14, Instant.now(), "rm")));

        AdoCredentialService.View view = service.status();

        assertThat(view.configured()).isTrue();
        assertThat(view.state()).isEqualTo(AdoCredentialState.EXPIRING);
    }

    @Test
    void statusIsUnconfiguredWhenNoRow() {
        when(repository.findStatus()).thenReturn(Optional.empty());

        assertThat(service.status().configured()).isFalse();
    }
}
