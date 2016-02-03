/*
 * Copyright 2016 Timothy Brooks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.uncontended.precipice.circuit;

import net.uncontended.precipice.Result;
import net.uncontended.precipice.metrics.ActionMetrics;
import net.uncontended.precipice.metrics.BackgroundTask;
import net.uncontended.precipice.metrics.HealthSnapshot;
import net.uncontended.precipice.time.Clock;
import net.uncontended.precipice.time.SystemTime;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SWCircuitBreaker implements CircuitBreaker, BackgroundTask {

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int FORCED_OPEN = 2;

    private final Clock systemTime;
    private final AtomicInteger state = new AtomicInteger(0);
    private volatile long lastTestedTime = 0;
    private volatile BreakerConfig breakerConfig;
    private volatile HealthSnapshot health = new HealthSnapshot(0, 0, 0, 0);
    private ActionMetrics<?> actionMetrics;

    public SWCircuitBreaker(BreakerConfig breakerConfig) {
        this(breakerConfig, new SystemTime());
    }

    public SWCircuitBreaker(BreakerConfig breakerConfig, Clock systemTime) {
        this.systemTime = systemTime;
        this.breakerConfig = breakerConfig;
    }

    @Override
    public boolean isOpen() {
        return state.get() != CLOSED;
    }

    @Override
    public boolean allowAction() {
        return allowAction(systemTime.nanoTime());
    }

    @Override
    public boolean allowAction(long nanoTime) {
        int state = this.state.get();
        if (state == OPEN) {
            long backOffTimeMillis = breakerConfig.backOffTimeMillis;
            long currentTime = currentMillisTime(nanoTime);
            if (currentTime < backOffTimeMillis + lastTestedTime) {
                return false;
            }
            lastTestedTime = currentTime;
        }
        return state != FORCED_OPEN;
    }

    @Override
    public void informBreakerOfResult(Result result) {
        informBreakerOfResult(result, systemTime.nanoTime());
    }

    @Override
    public void informBreakerOfResult(Result result, long nanoTime) {
        if (result.isSuccess()) {
            if (state.get() == OPEN) {
                // This can get stuck in a loop with open and closing
                state.compareAndSet(OPEN, CLOSED);
            }
        } else {
            if (state.get() == CLOSED) {
                long currentTime = currentMillisTime(nanoTime);
                BreakerConfig config = breakerConfig;
                HealthSnapshot health = this.health;
                long failures = health.failures;
                int failurePercentage = health.failurePercentage();
                if (config.failureThreshold < failures || (config.failurePercentageThreshold < failurePercentage &&
                        config.sampleSizeThreshold < health.total)) {
                    lastTestedTime = currentTime;
                    state.compareAndSet(CLOSED, OPEN);
                }
            }
        }
    }

    @Override
    public BreakerConfig getBreakerConfig() {
        return breakerConfig;
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {
        this.breakerConfig = breakerConfig;
    }

    @Override
    public void setActionMetrics(ActionMetrics<?> actionMetrics) {
        this.actionMetrics = actionMetrics;
    }

    @Override
    public void forceOpen() {
        state.set(FORCED_OPEN);
    }

    @Override
    public void forceClosed() {
        state.set(CLOSED);
    }

    @Override
    public void tick(long currentTime) {
        health = actionMetrics.healthSnapshot(breakerConfig.trailingPeriodMillis, TimeUnit.MILLISECONDS);
    }

    private static long currentMillisTime(long nanoTime) {
        return TimeUnit.MILLISECONDS.convert(nanoTime, TimeUnit.NANOSECONDS);
    }
}
