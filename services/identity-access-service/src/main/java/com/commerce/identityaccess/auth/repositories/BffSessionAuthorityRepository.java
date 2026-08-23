package com.commerce.identityaccess.auth.repositories;

import com.commerce.identityaccess.auth.models.BffSessionAuthorityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BffSessionAuthorityRepository extends JpaRepository<BffSessionAuthorityEntity, UUID> {
    List<BffSessionAuthorityEntity> findAllBySessionId(UUID sessionId);
}
