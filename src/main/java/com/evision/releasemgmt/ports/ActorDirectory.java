package com.evision.releasemgmt.ports;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves Jira account ids to the email stored alongside them. Deliberately separate from
 * JiraCredentialRepository and deliberately one method wide: nothing declared here can return the
 * stored API token, so a display path cannot reach the decryption that a superseded
 * RM_CREDENTIAL_KEY would fail (D-05.3-03). Do not add a method returning JiraCredential.
 */
public interface ActorDirectory {

    /** Emails by account id. Account ids with no credential row are simply absent from the map. */
    Map<String, String> findEmailsByAccountId(Collection<String> accountIds);
}
