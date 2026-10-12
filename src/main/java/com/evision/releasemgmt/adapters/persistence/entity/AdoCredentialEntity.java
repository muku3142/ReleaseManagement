package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.adapters.persistence.crypto.EncryptedTokenConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ado_credential")
public class AdoCredentialEntity {

    @Id
    private Short id;

    @Column(name = "organization", nullable = false)
    private String organization;

    @Column(name = "encrypted_token", nullable = false)
    @Convert(converter = EncryptedTokenConverter.class)
    private String token;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    public AdoCredentialEntity() {
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public short getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(short keyVersion) {
        this.keyVersion = keyVersion;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
