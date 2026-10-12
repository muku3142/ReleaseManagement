package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
import com.evision.releasemgmt.ports.EnvironmentLadderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentLadderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final EnvironmentLadderRepository repository = mock(EnvironmentLadderRepository.class);
    private final EnvironmentLadderService service =
            new EnvironmentLadderService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage test = new Stage(UUID.randomUUID(), "TEST", 2, false, null);
    private final Stage uat = new Stage(UUID.randomUUID(), "UAT", 3, false, null);
    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 4, true, null);

    @BeforeEach
    void ladder() {
        when(repository.findAll()).thenReturn(List.of(dev, test, uat, prod));
        when(repository.findReferencedStageIds()).thenReturn(Set.of());
    }

    private static void assertRejected(Runnable action, LadderEditRejection reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LadderEditRejectedException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void addAndRenameTrimTheName() {
        service.add("  QA  ");
        service.rename(dev.id(), "  Development ");

        verify(repository).add("QA");
        verify(repository).rename(dev.id(), "Development");
    }

    @Test
    void unknownIdsAreNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.rename(unknown, "X")).isInstanceOf(StageNotFoundException.class);
        assertThatThrownBy(() -> service.retire(unknown)).isInstanceOf(StageNotFoundException.class);
        assertThatThrownBy(() -> service.restore(unknown)).isInstanceOf(StageNotFoundException.class);
        assertThatThrownBy(() -> service.moveProduction(unknown)).isInstanceOf(StageNotFoundException.class);
        assertThatThrownBy(() -> service.delete(unknown)).isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void reorderRequiresAPermutationOfEveryStage() {
        assertRejected(() -> service.reorder(List.of(dev.id(), test.id(), uat.id())),
                LadderEditRejection.STAGE_ORDER_MISMATCH);
        assertRejected(() -> service.reorder(List.of(dev.id(), dev.id(), uat.id(), prod.id())),
                LadderEditRejection.STAGE_ORDER_MISMATCH);
        assertRejected(() -> service.reorder(List.of(dev.id(), test.id(), uat.id(), UUID.randomUUID())),
                LadderEditRejection.STAGE_ORDER_MISMATCH);
        assertRejected(() -> service.reorder(List.of(dev.id(), test.id(), uat.id(), prod.id(), UUID.randomUUID())),
                LadderEditRejection.STAGE_ORDER_MISMATCH);
        verify(repository, never()).reorder(org.mockito.ArgumentMatchers.anyList());

        List<UUID> swapped = List.of(test.id(), dev.id(), uat.id(), prod.id());
        service.reorder(swapped);
        verify(repository).reorder(swapped);
    }

    @Test
    void reorderAcceptsRetiredStagesInThePermutation() {
        Stage retiredTest = new Stage(test.id(), "TEST", 2, false, NOW);
        when(repository.findAll()).thenReturn(List.of(dev, retiredTest, uat, prod));
        List<UUID> order = List.of(prod.id(), test.id(), dev.id(), uat.id());

        service.reorder(order);

        verify(repository).reorder(order);
    }

    @Test
    void retireRules() {
        assertRejected(() -> service.retire(prod.id()), LadderEditRejection.PRODUCTION_STAGE_PROTECTED);

        service.retire(test.id());
        verify(repository).setRetiredAt(test.id(), NOW);
    }

    @Test
    void retiringTheOnlyActiveStageIsRejected() {
        Stage only = new Stage(UUID.randomUUID(), "ONLY", 1, false, null);
        Stage gone = new Stage(UUID.randomUUID(), "GONE", 2, false, NOW);
        when(repository.findAll()).thenReturn(List.of(only, gone));

        assertRejected(() -> service.retire(only.id()), LadderEditRejection.LAST_ACTIVE_STAGE);
    }

    @Test
    void retiringARetiredStageIsANoOp() {
        Stage retired = new Stage(test.id(), "TEST", 2, false, NOW);
        when(repository.findAll()).thenReturn(List.of(dev, retired, uat, prod));

        service.retire(test.id());

        verify(repository, never()).setRetiredAt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restoreClearsRetirementAndIsANoOpOnActiveStages() {
        Stage retired = new Stage(test.id(), "TEST", 2, false, NOW);
        when(repository.findAll()).thenReturn(List.of(dev, retired, uat, prod));

        service.restore(test.id());
        service.restore(dev.id());

        verify(repository).setRetiredAt(test.id(), null);
        verify(repository, never()).setRetiredAt(dev.id(), null);
    }

    @Test
    void productionFlagMovesOnlyToAnActiveStage() {
        Stage retired = new Stage(test.id(), "TEST", 2, false, NOW);
        when(repository.findAll()).thenReturn(List.of(dev, retired, uat, prod));

        assertRejected(() -> service.moveProduction(test.id()), LadderEditRejection.STAGE_RETIRED);

        service.moveProduction(prod.id());
        verify(repository, never()).moveProductionFlag(prod.id());

        service.moveProduction(uat.id());
        verify(repository).moveProductionFlag(uat.id());
    }

    @Test
    void deleteRules() {
        assertRejected(() -> service.delete(prod.id()), LadderEditRejection.PRODUCTION_STAGE_PROTECTED);

        when(repository.findReferencedStageIds()).thenReturn(Set.of(dev.id()));
        assertRejected(() -> service.delete(dev.id()), LadderEditRejection.STAGE_IN_USE);
        verify(repository, never()).delete(dev.id());

        service.delete(uat.id());
        verify(repository).delete(uat.id());
    }

    @Test
    void deletingTheLastActiveStageIsRejected() {
        Stage only = new Stage(UUID.randomUUID(), "ONLY", 1, false, null);
        when(repository.findAll()).thenReturn(List.of(only));

        assertRejected(() -> service.delete(only.id()), LadderEditRejection.LAST_ACTIVE_STAGE);
    }

    @Test
    void noPublicOperationClearsTheProductionFlagWithoutSettingAnother() {
        List<String> operations = Arrays.stream(EnvironmentLadderService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(operations).containsExactly(
                "add", "delete", "ladder", "moveProduction", "referencedStageIds", "rename", "reorder",
                "restore", "retire");
    }
}
