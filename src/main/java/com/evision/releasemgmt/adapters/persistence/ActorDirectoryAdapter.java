package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.repository.JiraCredentialJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.JiraCredentialJpaRepository.AccountEmail;
import com.evision.releasemgmt.ports.ActorDirectory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ActorDirectoryAdapter implements ActorDirectory {

    private final JiraCredentialJpaRepository jpa;

    public ActorDirectoryAdapter(JiraCredentialJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<String, String> findEmailsByAccountId(Collection<String> accountIds) {
        return jpa.findEmailsByAccountIdIn(accountIds).stream()
                .collect(Collectors.toMap(AccountEmail::getAccountId, AccountEmail::getEmail));
    }
}
