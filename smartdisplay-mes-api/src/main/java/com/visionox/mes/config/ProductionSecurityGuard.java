package com.visionox.mes.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 生产 profile 启动校验（仅 {@code prod} profile 生效）：拒绝使用仓库内置的默认 JWT 密钥 / 数据库口令，
 * 强制通过环境变量注入独立凭据。默认 / docker 演示 profile 不受影响。
 *
 * <p>对应代码审查 1.1（DB 口令默认值）、1.2（JWT 密钥默认值）：保留一键演示的默认值便利，
 * 同时在真正的生产 profile 下 fail-fast，避免默认凭据被带到生产。</p>
 */
@Configuration
@Profile("prod")
public class ProductionSecurityGuard {

    static final String DEFAULT_JWT_SECRET = "smartdisplay-mes-jwt-secret-key-2024-very-long-key-for-hs256";
    static final String DEFAULT_DB_PASSWORD = "mes123456";

    private final String jwtSecret;
    private final String dbPassword;

    public ProductionSecurityGuard(
            @Value("${mes.security.jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String dbPassword) {
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    void verifySecretsAreInjected() {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "生产环境必须通过 MES_JWT_SECRET 注入独立 JWT 密钥，不能使用内置默认值");
        }
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            throw new IllegalStateException(
                    "生产环境必须通过 SPRING_DATASOURCE_PASSWORD 注入独立数据库口令，不能使用内置默认值");
        }
    }
}
