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

import net.uncontended.precipice.Failable;
import net.uncontended.precipice.GuardRail;
import net.uncontended.precipice.metrics.Rolling;
import net.uncontended.precipice.metrics.counts.PartitionedCount;
import net.uncontended.precipice.metrics.counts.WritableCounts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unstable and still in development. At this time, {@link DefaultCircuitBreaker} should be used.
 */
public class SWCircuitBreaker<Rejected extends Enum<Rejected>> implements CircuitBreaker<Rejected>, BackgroundTask {
    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int FORCED_OPEN = 2;

    private final AtomicInteger state = new AtomicInteger(0);
    private final HealthGauge healthGauge;
    private volatile long lastTestedNanoTime = 0;
    private volatile CircuitBreakerConfig<Rejected> breakerConfig;
    private volatile HealthSnapshot health = new HealthSnapshot(0, 0);

    public SWCircuitBreaker(CircuitBreakerConfig<Rejected> breakerConfig) {
        this(breakerConfig, new HealthGauge());
    }

    public SWCircuitBreaker(CircuitBreakerConfig<Rejected> breakerConfig, HealthGauge healthGauge) {
        this.breakerConfig = breakerConfig;
        this.healthGauge = healthGauge;
    }

    @Override
    public Rejected acquirePermit(long number, long nanoTime) {
        CircuitBreakerConfig<Rejected> config = breakerConfig;
        int state = this.state.get();
        if (state == OPEN) {
            long backOffTimeNanos = config.backOffTimeNanos;
            if (nanoTime - (backOffTimeNanos + lastTestedNanoTime) < 0) {
                return config.reason;
            }
            lastTestedNanoTime = nanoTime;
        }
        return state != FORCED_OPEN ? null : config.reason;
    }

    @Override
    public void releasePermit(long number, long nanoTime) {
    }

    @Override
    public void releasePermit(long number, Failable result, long nanoTime) {
        if (result.isSuccess()) {
            if (state.get() == OPEN) {
                state.compareAndSet(OPEN, CLOSED);
            }
        } else {
            if (state.get() == CLOSED) {
                CircuitBreakerConfig<Rejected> config = breakerConfig;
                HealthSnapshot health = this.health;
                long failures = health.failures;
                int failurePercentage = health.failurePercentage();
                if (config.failureThreshold < failures || (config.failurePercentageThreshold < failurePercentage &&
                        config.sampleSizeThreshold < health.total)) {
                    lastTestedNanoTime = nanoTime;
                    state.compareAndSet(CLOSED, OPEN);
                }
            }
        }
    }

    @Override
    public <Result extends Enum<Result> & Failable> void registerGuardRail(GuardRail<Result, Rejected> guardRail) {
        WritableCounts<Result> metrics = guardRail.getResultCounts();
        if (metrics instanceof Rolling) {
            healthGauge.add((Rolling<PartitionedCount<Result>>) metrics);
        } else {
            throw new IllegalArgumentException("DefaultCircuitBreaker requires rolling result object");
        }
    }

    @Override
    public boolean isOpen() {
        return state.get() != CLOSED;
    }

    @Override
    public CircuitBreakerConfig<Rejected> getBreakerConfig() {
        return breakerConfig;
    }

    @Override
    public void setBreakerConfig(CircuitBreakerConfig<Rejected> breakerConfig) {
        this.breakerConfig = breakerConfig;
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
    public void tick(long nanoTime) {
        health = healthGauge.getHealth(breakerConfig.trailingPeriodNanos, TimeUnit.NANOSECONDS, nanoTime);
    }
}
