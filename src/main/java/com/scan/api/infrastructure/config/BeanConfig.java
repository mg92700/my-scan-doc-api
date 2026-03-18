package com.scan.api.infrastructure.config;

import com.scan.api.application.service.DocumentService;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.OutboxPort;
import com.scan.api.domain.port.out.StoragePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring hexagonal — c'est ici que Spring assemble les ports et les adapters.
 *
 * DocumentService (domaine) ne porte aucune annotation @Service :
 * il reste un POJO pur. Spring le connaît uniquement via ce @Bean.
 *
 * Les implémentations concrètes (DocumentJpaAdapter, S3StorageAdapter,
 * OutboxJdbcAdapter) sont injectées automatiquement car annotées @Component.
 */
@Configuration
public class BeanConfig {

    @Bean
    public DocumentUseCase documentUseCase(
            DocumentRepositoryPort repository,
            StoragePort storage,
            OutboxPort outbox
    ) {
        return new DocumentService(repository, storage, outbox);
    }
}
