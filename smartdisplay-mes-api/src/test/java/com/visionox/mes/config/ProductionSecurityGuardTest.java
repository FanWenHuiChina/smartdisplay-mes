package com.visionox.mes.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityGuardTest {

    @Test
    void rejectsDefaultJwtSecret() {
        ProductionSecurityGuard guard = new ProductionSecurityGuard(
                ProductionSecurityGuard.DEFAULT_JWT_SECRET, "injected-db-password");
        assertThatThrownBy(guard::verifySecretsAreInjected)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MES_JWT_SECRET");
    }

    @Test
    void rejectsDefaultDbPassword() {
        ProductionSecurityGuard guard = new ProductionSecurityGuard(
                "injected-jwt-secret", ProductionSecurityGuard.DEFAULT_DB_PASSWORD);
        assertThatThrownBy(guard::verifySecretsAreInjected)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    void passesWhenSecretsAreInjected() {
        ProductionSecurityGuard guard = new ProductionSecurityGuard(
                "injected-jwt-secret", "injected-db-password");
        assertThatCode(guard::verifySecretsAreInjected).doesNotThrowAnyException();
    }
}
