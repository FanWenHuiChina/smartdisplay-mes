package com.visionox.mes.auth.security;

import com.visionox.mes.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private long now = 1_000_000L;
    private final LoginAttemptService service = new LoginAttemptService(() -> now);

    @Test
    void doesNotLockBelowThreshold() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("bob");
        }
        assertThatCode(() -> service.assertNotLocked("bob")).doesNotThrowAnyException();
    }

    @Test
    void locksAfterMaxFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("bob");
        }
        assertThatThrownBy(() -> service.assertNotLocked("bob"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    void lockIsCaseAndWhitespaceInsensitiveOnUsername() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("Bob");
        }
        assertThatThrownBy(() -> service.assertNotLocked("  bob  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("bob");
        }
        service.recordSuccess("bob");
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("bob");
        }
        assertThatCode(() -> service.assertNotLocked("bob")).doesNotThrowAnyException();
    }

    @Test
    void unlocksAfterCooldownWindow() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("bob");
        }
        now += LoginAttemptService.LOCK_WINDOW_MS + 1;
        assertThatCode(() -> service.assertNotLocked("bob")).doesNotThrowAnyException();
    }
}
