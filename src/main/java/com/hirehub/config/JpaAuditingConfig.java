package com.hirehub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activates the {@code @CreatedDate} / {@code @LastModifiedDate} callbacks used by
 * {@link com.hirehub.entity.AuditableEntity}.
 *
 * <p>Kept in its own class rather than on {@code HireHubApplication}: a test that slices the
 * context can import exactly this configuration, and the main class stays free of
 * feature-specific annotations.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
