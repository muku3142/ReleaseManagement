package com.evision.releasemgmt.adapters.persistence.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedTokenConverterTest {

    private static final String FIXTURE_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String PLAINTEXT = "ATATT3xFfGF0-secret-token";

    private final EncryptedTokenConverter converter = new EncryptedTokenConverter(FIXTURE_KEY, 1);

    @Test
    void roundTripsThroughTheEnvelope() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(PLAINTEXT);
    }

    @Test
    void storedValueIsNotPlaintext() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        assertThat(stored).doesNotContain("ATATT3xFfGF0").isNotEqualTo(PLAINTEXT);
    }

    @Test
    void freshIvProducesDifferentCiphertextEachTime() {
        String first = converter.convertToDatabaseColumn(PLAINTEXT);
        String second = converter.convertToDatabaseColumn(PLAINTEXT);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void envelopeLayoutIsVersionThenIvThenCiphertextPlusTag() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        byte[] raw = Base64.getDecoder().decode(stored);
        assertThat(raw[0]).isEqualTo((byte) 1);
        assertThat(raw.length).isEqualTo(1 + 12 + PLAINTEXT.getBytes().length + 16);
    }

    @Test
    void nullInNullOutBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void tamperingWithCiphertextFailsTheGcmTagCheck() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        byte[] raw = Base64.getDecoder().decode(stored);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortKeyFailsConstructionWith32BytesMessage() {
        String sixteenByteKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new EncryptedTokenConverter(sixteenByteKey, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void differentKeyCannotDecryptAnotherKeysValue() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        String otherKey = Base64.getEncoder().encodeToString(new byte[32]);
        EncryptedTokenConverter otherConverter = new EncryptedTokenConverter(otherKey, 1);

        assertThatThrownBy(() -> otherConverter.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
    }
}
