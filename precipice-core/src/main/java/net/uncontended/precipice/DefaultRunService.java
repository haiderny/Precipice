/*
 * Copyright 2014 Timothy Brooks
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

package net.uncontended.precipice;

import net.uncontended.precipice.circuit.CircuitBreaker;
import net.uncontended.precipice.metrics.ActionMetrics;
import net.uncontended.precipice.metrics.LatencyMetrics;
import net.uncontended.precipice.timeout.ActionTimeoutException;

public class DefaultRunService implements RunService {

    private final NewController<Status> controller;

    public DefaultRunService(NewController<Status> controller) {
        this.controller = controller;
    }

    @Override
    public <T> T run(final ResilientAction<T> action) throws Exception {
        RejectionReason rejectionReason = controller.acquirePermitOrGetRejectedReason();
        if (rejectionReason != null) {
            handleRejectedReason(rejectionReason);
        }
        long nanoStart = System.nanoTime();
        try {
            T result = action.run();
            metricsAndBreakerFeedback(nanoStart, Status.SUCCESS);
            return result;
        } catch (ActionTimeoutException e) {
            metricsAndBreakerFeedback(nanoStart, Status.TIMEOUT);
            throw e;
        } catch (Exception e) {
            metricsAndBreakerFeedback(nanoStart, Status.ERROR);
            throw e;
        } finally {
            controller.getSemaphore().releasePermit();
        }
    }

    @Override
    public String getName() {
        return controller.getName();
    }

    @Override
    public ActionMetrics<Status> getActionMetrics() {
        return controller.getActionMetrics();
    }

    @Override
    public LatencyMetrics<Status> getLatencyMetrics() {
        return controller.getLatencyMetrics();
    }

    @Override
    public CircuitBreaker getCircuitBreaker() {
        return controller.getCircuitBreaker();
    }

    @Override
    public int remainingCapacity() {
        return controller.remainingCapacity();
    }

    @Override
    public int pendingCount() {
        return controller.pendingCount();
    }

    @Override
    public void shutdown() {
        controller.shutdown();
    }

    private void handleRejectedReason(RejectionReason rejectionReason) {
        if (rejectionReason == RejectionReason.CIRCUIT_OPEN) {
            controller.getActionMetrics().incrementRejectionCount(RejectionReason.CIRCUIT_OPEN);
        } else if (rejectionReason == RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED) {
            controller.getActionMetrics().incrementRejectionCount(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        }
        throw new RejectedActionException(rejectionReason);
    }

    private void metricsAndBreakerFeedback(long nanoStart, Status status) {
        long nanoTime = System.nanoTime();
        controller.getActionMetrics().incrementMetricCount(status, nanoTime);
        controller.getCircuitBreaker().informBreakerOfResult(status.isSuccess(), nanoTime);
        long latency = nanoTime - nanoStart;
        controller.getLatencyMetrics().recordLatency(status, latency, nanoTime);
    }
}
