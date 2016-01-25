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

package net.uncontended.precipice.pattern;

import net.uncontended.precipice.*;
import net.uncontended.precipice.concurrent.Eventual;
import net.uncontended.precipice.concurrent.PrecipicePromise;
import net.uncontended.precipice.metrics.ActionMetrics;
import net.uncontended.precipice.metrics.LatencyMetrics;

import java.util.concurrent.atomic.AtomicReference;

public class PatternController<T extends Enum<T> & Result> {
    private final ActionMetrics<T> actionMetrics;
    private final LatencyMetrics<T> latencyMetrics;
    private final String name;
    private final AtomicReference<Controller<T>[]> children;
    private volatile boolean isShutdown = false;

    public PatternController(String name, PatternControllerProperties<T> properties) {
        this(name, properties.actionMetrics(), properties.latencyMetrics());
    }

    public PatternController(String name, ActionMetrics<T> actionMetrics, LatencyMetrics<T> latencyMetrics) {
        this.actionMetrics = actionMetrics;
        this.latencyMetrics = latencyMetrics;
        this.name = name;
        children = new AtomicReference<>((Controller<T>[]) new Controller[0]);
    }

    public String getName() {
        return name;
    }

    public ActionMetrics<T> getActionMetrics() {
        return actionMetrics;
    }

    public LatencyMetrics<T> getLatencyMetrics() {
        return latencyMetrics;
    }

    public void ensureNotShutdown() {
        if (isShutdown) {
            throw new IllegalStateException("Service has been shutdown.");
        }
    }

    public <R> PrecipicePromise<T, R> acquirePermitAndGetPromise(Controller<T>[] controllers) {
        ensureNotShutdown();
        long startTime = System.nanoTime();

        Controller<T> controllerToUse = null;
        for (Controller<T> controller : controllers) {
            Rejected rejected = controller.acquirePermitOrGetRejectedReason();
            if (rejected != null) {
                controllerToUse = controller;
            }
        }
        if (controllerToUse != null) {
            return getPromise(startTime);
        } else {
            actionMetrics.incrementRejectionCount(null, startTime);
            throw new RejectedException(null);
        }
    }

    public <R> PrecipicePromise<T, R> getPromise(long startTime) {
        Eventual<T, R> promise = new Eventual<>(startTime);
        promise.internalOnComplete(new PrecipiceFunction<T, PerformingContext>() {
            @Override
            public void apply(T status, PerformingContext eventual) {
                long endTime = System.nanoTime();
                actionMetrics.incrementMetricCount(status);
                latencyMetrics.recordLatency(status, endTime - eventual.startNanos(), endTime);
            }
        });
        return promise;
    }

    public Controller<T>[] getChildControllers() {
        return children.get();
    }

    public void shutdown() {
        isShutdown = true;
    }
}