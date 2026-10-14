package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.config.ChatProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationGateTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void clientAtItsLimitCannotAcquireASecondSlot() {
        GenerationGate gate = gate(1, 4);
        assertThat(gate.tryAcquire(a)).isTrue();
        assertThat(gate.tryAcquire(a)).isFalse();
        assertThat(gate.tryAcquire(b)).isTrue();
    }

    @Test
    void releaseFreesTheSlotAndAnyInFlightTracksHolders() {
        GenerationGate gate = gate(1, 4);
        assertThat(gate.anyInFlight()).isFalse();
        gate.tryAcquire(a);
        gate.tryAcquire(b);
        assertThat(gate.anyInFlight()).isTrue();
        gate.release(a);
        assertThat(gate.tryAcquire(a)).isTrue();
        gate.release(a);
        gate.release(b);
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void globalCapRefusesAnotherClient() {
        GenerationGate gate = gate(1, 2);
        gate.tryAcquire(a);
        gate.tryAcquire(b);
        assertThat(gate.tryAcquire(c)).isFalse();
    }

    @Test
    void releaseWithoutSlotDoesNotGoNegative() {
        GenerationGate gate = gate(1, 2);
        gate.release(a);
        assertThat(gate.anyInFlight()).isFalse();
        assertThat(gate.tryAcquire(a)).isTrue();
        assertThat(gate.tryAcquire(b)).isTrue();
        assertThat(gate.tryAcquire(c)).isFalse();
    }

    private static GenerationGate gate(int perClient, int global) {
        return new GenerationGate(new ChatProperties("m", 8192, 768, 0.2, 0.3, "30m", null, null, perClient, global,
                5, 5, 8, 0.55, 2000, 20, 4000, 1500, 2000, 3.0));
    }
}
