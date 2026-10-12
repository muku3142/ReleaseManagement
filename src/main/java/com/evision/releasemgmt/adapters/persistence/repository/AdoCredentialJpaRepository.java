package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.AdoCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The write and metadata operations here deliberately avoid loading the entity. Loading it
 * decrypts {@code encrypted_token} through EncryptedTokenConverter, so a findById on a row
 * encrypted under a superseded key would fail, including the save that replaces that token.
 */
public interface AdoCredentialJpaRepository extends JpaRepository<AdoCredentialEntity, Short> {

    @Modifying
    @Query("""
            update AdoCredentialEntity e
               set e.organization = :organization,
                   e.token = :token,
                   e.keyVersion = :keyVersion,
                   e.expiresOn = :expiresOn,
                   e.updatedAt = :now,
                   e.updatedBy = :updatedBy
             where e.id = 1
            """)
    int replace(@Param("organization") String organization,
                @Param("token") String token,
                @Param("keyVersion") short keyVersion,
                @Param("expiresOn") LocalDate expiresOn,
                @Param("now") Instant now,
                @Param("updatedBy") String updatedBy);

    @Modifying
    @Query("""
            update AdoCredentialEntity e
               set e.expiresOn = :expiresOn,
                   e.updatedAt = :now,
                   e.updatedBy = :updatedBy
             where e.id = 1 and e.organization = :organization
            """)
    int updateExpiry(@Param("organization") String organization,
                     @Param("expiresOn") LocalDate expiresOn,
                     @Param("now") Instant now,
                     @Param("updatedBy") String updatedBy);

    @Query("""
            select e.organization as organization, e.expiresOn as expiresOn,
                   e.updatedAt as updatedAt, e.updatedBy as updatedBy
              from AdoCredentialEntity e
             where e.id = 1
            """)
    Optional<StatusRow> findStatusRow();

    /** Scalar columns only, so no converter runs. */
    interface StatusRow {
        String getOrganization();
        LocalDate getExpiresOn();
        Instant getUpdatedAt();
        String getUpdatedBy();
    }
}
