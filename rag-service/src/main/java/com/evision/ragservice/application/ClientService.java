package com.evision.ragservice.application;

import com.evision.ragservice.domain.ApiKeys;
import com.evision.ragservice.domain.ClientApp;
import com.evision.ragservice.domain.ClientNameTakenException;
import com.evision.ragservice.domain.ClientNotFoundException;
import com.evision.ragservice.ports.ClientStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClientService {

    public record IssuedKey(ClientApp client, String apiKey) {
    }

    private final ClientStore store;

    public ClientService(ClientStore store) {
        this.store = store;
    }

    public IssuedKey register(String name) {
        String key = ApiKeys.generate();
        ClientApp client = store.create(name, ApiKeys.hash(key)).orElseThrow(() -> new ClientNameTakenException(name));
        return new IssuedKey(client, key);
    }

    public IssuedKey rotate(UUID id) {
        String key = ApiKeys.generate();
        if (!store.replaceKey(id, ApiKeys.hash(key))) {
            throw new ClientNotFoundException(id);
        }
        return new IssuedKey(store.find(id).orElseThrow(() -> new ClientNotFoundException(id)), key);
    }

    public void revoke(UUID id) {
        if (!store.revoke(id)) {
            throw new ClientNotFoundException(id);
        }
    }

    public List<ClientApp> list() {
        return store.list();
    }

    public Optional<UUID> authenticate(String presentedKey) {
        Optional<UUID> id = store.findActiveIdByKeyHash(ApiKeys.hash(presentedKey));
        id.ifPresent(store::touchLastUsed);
        return id;
    }
}
