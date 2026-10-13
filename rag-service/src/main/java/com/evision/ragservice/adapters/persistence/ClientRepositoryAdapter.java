package com.evision.ragservice.adapters.persistence;

import com.evision.ragservice.domain.ClientApp;
import com.evision.ragservice.ports.ClientStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ClientRepositoryAdapter implements ClientStore {

    private final JdbcClient jdbc;

    ClientRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<ClientApp> create(String name, byte[] keyHash) {
        return jdbc.sql("""
                        INSERT INTO client_app (name, key_hash) VALUES (:name, :hash)
                        ON CONFLICT (name) DO NOTHING
                        RETURNING id, name, created_at, revoked_at, last_used_at
                        """)
                .param("name", name).param("hash", keyHash)
                .query(ClientRepositoryAdapter::map).optional();
    }

    @Override
    public Optional<UUID> findActiveIdByKeyHash(byte[] keyHash) {
        return jdbc.sql("SELECT id FROM client_app WHERE key_hash = :hash AND revoked_at IS NULL")
                .param("hash", keyHash).query(UUID.class).optional();
    }

    @Override
    @Transactional
    public void touchLastUsed(UUID clientId) {
        jdbc.sql("""
                        UPDATE client_app SET last_used_at = now()
                        WHERE id = :id AND (last_used_at IS NULL OR last_used_at < now() - interval '1 minute')
                        """)
                .param("id", clientId).update();
    }

    @Override
    @Transactional
    public boolean replaceKey(UUID clientId, byte[] keyHash) {
        return jdbc.sql("UPDATE client_app SET key_hash = :hash WHERE id = :id AND revoked_at IS NULL")
                .param("hash", keyHash).param("id", clientId).update() == 1;
    }

    @Override
    @Transactional
    public boolean revoke(UUID clientId) {
        return jdbc.sql("UPDATE client_app SET revoked_at = COALESCE(revoked_at, now()) WHERE id = :id")
                .param("id", clientId).update() == 1;
    }

    @Override
    public Optional<ClientApp> find(UUID clientId) {
        return jdbc.sql("SELECT id, name, created_at, revoked_at, last_used_at FROM client_app WHERE id = :id")
                .param("id", clientId).query(ClientRepositoryAdapter::map).optional();
    }

    @Override
    public List<ClientApp> list() {
        return jdbc.sql("SELECT id, name, created_at, revoked_at, last_used_at FROM client_app ORDER BY name")
                .query(ClientRepositoryAdapter::map).list();
    }

    private static ClientApp map(ResultSet rs, int row) throws SQLException {
        return new ClientApp(rs.getObject("id", UUID.class), rs.getString("name"),
                instant(rs, "created_at"), instant(rs, "revoked_at"), instant(rs, "last_used_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
