package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.adapters.persistence.crypto.EncryptedTokenConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "jira_credential")
public class JiraCredentialEntity {

    @Id
    @Column(name = "jira_account_id", nullable = false)
    private String jiraAccountId;

    @Column(name = "jira_email", nullable = false)
    private String jiraEmail;

    @Column(name = "encrypted_token", nullable = false)
    @Convert(converter = EncryptedTokenConverter.class)
    private String token;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JiraCredentialEntity() {
    }

    public String getJiraAccountId() {
        return jiraAccountId;
    }

    public void setJiraAccountId(String jiraAccountId) {
        this.jiraAccountId = jiraAccountId;
    }

    public String getJiraEmail() {
        return jiraEmail;
    }

    public void setJiraEmail(String jiraEmail) {
        this.jiraEmail = jiraEmail;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
