package com.scan.api.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data — package-private, invisible du reste de l'application.
 * Utilisé uniquement par DocumentJpaAdapter.
 */
interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    Optional<DocumentJpaEntity> findBySha256(String sha256);
}
