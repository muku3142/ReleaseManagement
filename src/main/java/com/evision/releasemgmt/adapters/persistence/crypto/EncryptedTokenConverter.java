package com.evision.releasemgmt.adapters.persistence.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JPA AttributeConverter storing a self-contained AES-256-GCM envelope:
 * keyVersion(1 byte) || IV(12 bytes) || ciphertext+tag, base64-encoded.
 * A fresh random IV is generated on every write; per D-11, key_version is
 * recorded metadata for a future manual rotation batch, never used here to
 * select a decryption key (only one key is ever active at a time).
 */
@Component
@Converter
public class EncryptedTokenConverter implements AttributeConverter<String, String> {

    private static final int IV_LENGTH = 12;      // GCM standard nonce length
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;      // AES-256

    private final SecretKey key;
    private final byte keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedTokenConverter(@Value("${app.credential.key}") String base64Key,
                                    @Value("${app.credential.key-version}") int keyVersion) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "RM_CREDENTIAL_KEY must decode to exactly 32 bytes for AES-256; got " + raw.length);
        }
        if (keyVersion < 1 || keyVersion > 127) {
            throw new IllegalStateException("Configured credential key version must be between 1 and 127");
        }
        this.key = new SecretKeySpec(raw, "AES");
        this.keyVersion = (byte) keyVersion;
    }

    @Override
    public String convertToDatabaseColumn(String plainToken) {
        if (plainToken == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainToken.getBytes(UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(1 + IV_LENGTH + ciphertext.length);
            buffer.put(keyVersion).put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt credential column", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored);
            // a truncated column would otherwise surface as BufferUnderflowException,
            // breaking this converter's single-exception-type contract for decrypt failures
            if (raw.length <= 1 + IV_LENGTH) {
                throw new IllegalStateException("Failed to decrypt credential column: envelope too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            buffer.get(); // stored key version — metadata only, not used for key selection in v1
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt credential column", e);
        }
    }
}
