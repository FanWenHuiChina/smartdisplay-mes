package com.visionox.mes.auth.security;

import com.visionox.mes.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 登录失败计数与临时锁定（防暴力破解 / 撞库）。
 *
 * <p>进程内按用户名统计连续失败：窗口内累计达到阈值后临时锁定一个冷却窗口，登录成功即清零。
 * 试点用进程内 Map 演示防刷与安全意识；多实例生产可平滑替换为 Redis 做跨实例计数与锁定。
 * 时间源通过构造注入，便于单测控制时钟、无需真实等待。</p>
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final long LOCK_WINDOW_MS = 15 * 60 * 1000L;

    private final LongSupplier clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(System::currentTimeMillis);
    }

    LoginAttemptService(LongSupplier clock) {
        this.clock = clock;
    }

    /** 登录前校验：若处于锁定窗口内，拒绝并提示大致剩余时间。 */
    public void assertNotLocked(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null) {
            return;
        }
        long now = clock.getAsLong();
        if (attempt.lockedUntil > now) {
            long remainingSeconds = (attempt.lockedUntil - now + 999) / 1000;
            throw new BusinessException(429, "登录失败次数过多，账号已临时锁定，请约 " + remainingSeconds + " 秒后重试");
        }
    }

    /** 记录一次登录失败；窗口内累计达到阈值则锁定。 */
    public void recordFailure(String username) {
        long now = clock.getAsLong();
        attempts.compute(key(username), (ignored, attempt) -> {
            if (attempt == null || now - attempt.lastFailureAt > LOCK_WINDOW_MS) {
                attempt = new Attempt();
            }
            attempt.failureCount++;
            attempt.lastFailureAt = now;
            if (attempt.failureCount >= MAX_FAILURES) {
                attempt.lockedUntil = now + LOCK_WINDOW_MS;
            }
            return attempt;
        });
    }

    /** 登录成功：清零该用户的失败计数与锁定。 */
    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempt {
        private int failureCount;
        private long lastFailureAt;
        private long lockedUntil;
    }
}
