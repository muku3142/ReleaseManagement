package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.JiraCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * The write and metadata operations here deliberately avoid loading the entity. Loading it
 * decrypts {@code encrypted_token} through EncryptedTokenConverter, so any operation that
 * did a findById would fail on a row encrypted under a superseded key — including the login
 * that is trying to replace that very token.
 */
public interface JiraCredentialJpaRepository extends JpaRepository<JiraCredentialEntity, String> {

    @Modifying
    @Query("""
            update JiraCredentialEntity e
               set e.jiraEmail = :email,
                   e.token = :token,
                   e.keyVersion = :keyVersion,
                   e.updatedAt = :now,
                   e.sessionsInvalidatedAt = null
             where e.jiraAccountId = :accountId
            """)
    int replaceCredential(@Param("accountId") String accountId,
                           @Param("email") String email,
                           @Param("token") String token,
                           @Param("keyVersion") short keyVersion,
                           @Param("now") Instant now);

    @Query("select e.sessionsInvalidatedAt from JiraCredentialEntity e where e.jiraAccountId = :accountId")
    Optional<Instant> findSessionsInvalidatedAt(@Param("accountId") String accountId);

    @Modifying
    @Query("update JiraCredentialEntity e set e.sessionsInvalidatedAt = :at where e.jiraAccountId = :accountId")
    int markSessionsInvalidated(@Param("accountId") String accountId, @Param("at") Instant at);
}
