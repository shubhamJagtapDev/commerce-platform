package com.commerce.identityaccess.auth.repositories;

import com.commerce.identityaccess.auth.models.BffSessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BffSessionRepository extends JpaRepository<BffSessionEntity, UUID> {
    @Query("select session from BffSessionEntity session where session.handleHash = :handleHash")
    Optional<BffSessionEntity> findByHandleHash(@Param("handleHash") byte[] handleHash);
}
