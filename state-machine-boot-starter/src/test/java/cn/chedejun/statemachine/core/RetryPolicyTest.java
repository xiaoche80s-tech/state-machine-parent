package cn.chedejun.statemachine.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test void nonePolicy_hasZeroMaxAttempts() {
        assertEquals(0, RetryPolicy.none().getMaxAttempts());
    }

    @Test void exponentialBackoff_calculatesCorrectDelay() {
        RetryPolicy p = RetryPolicy.exponentialBackoff()
                .initialDelay(1, TimeUnit.SECONDS).maxDelay(30, TimeUnit.SECONDS)
                .backoffFactor(2.0).maxAttempts(4).build();
        assertEquals(1000, p.getDelayForAttempt(1));
        assertEquals(2000, p.getDelayForAttempt(2));
        assertEquals(4000, p.getDelayForAttempt(3));
        assertEquals(8000, p.getDelayForAttempt(4));
    }

    @Test void exponentialBackoff_capsAtMaxDelay() {
        RetryPolicy p = RetryPolicy.exponentialBackoff()
                .initialDelay(1, TimeUnit.SECONDS).maxDelay(5, TimeUnit.SECONDS)
                .backoffFactor(2.0).build();
        assertEquals(5000, p.getDelayForAttempt(4));
        assertEquals(5000, p.getDelayForAttempt(5));
    }

    @Test void zeroAttempt_returnsZeroDelay() {
        assertEquals(0, RetryPolicy.exponentialBackoff().build().getDelayForAttempt(0));
    }
}
