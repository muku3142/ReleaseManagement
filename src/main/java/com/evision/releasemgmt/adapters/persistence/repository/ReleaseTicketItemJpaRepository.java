package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseTicketItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseTicketItemJpaRepository extends JpaRepository<ReleaseTicketItemEntity, UUID> {

    // D-03-04: deterministic order, ticket key ascending. There is no position column to sort by
    // and none is wanted.
    List<ReleaseTicketItemEntity> findByReleaseIdOrderByTicketKeyAsc(UUID releaseId);

    Optional<ReleaseTicketItemEntity> findByReleaseIdAndTicketKey(UUID releaseId, String ticketKey);

    List<ReleaseTicketItemEntity> findByTicketKey(String ticketKey);

    long deleteByReleaseIdAndTicketKey(UUID releaseId, String ticketKey);

    long deleteByReleaseIdAndBundle(UUID releaseId, String bundle);

    @Query("select distinct i.ticketKey from ReleaseTicketItemEntity i where i.ticketKey in :keys")
    List<String> findTicketKeysIn(@Param("keys") Collection<String> keys);
}
