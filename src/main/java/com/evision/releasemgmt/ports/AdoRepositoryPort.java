package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;

import java.util.List;

public interface AdoRepositoryPort {

    /** One cheap vso.code call proving token validity, Code (Read) scope and the organization name together. */
    void validate(String organization, String token);

    /**
     * All commits of one pull request. URLs are built only from the validated parts; nothing from a payload is fetched.
     *
     * @throws com.evision.releasemgmt.domain.ado.AdoAccessException with the failure reason (TOO_LARGE past the page ceiling)
     */
    List<LinkedCommit> pullRequestCommits(AdoCredential credential, AdoRepoRef repository, String repositoryId,
                                          String pullRequestId);
}
