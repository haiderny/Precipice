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

package net.uncontended.precipice.pattern;

import net.uncontended.precipice.Controller;
import net.uncontended.precipice.Rejected;
import net.uncontended.precipice.RejectedException;
import net.uncontended.precipice.Result;
import net.uncontended.precipice.concurrent.PrecipicePromise;

import java.util.List;

public class Balancer<T extends Enum<T> & Result> {

    private final Controller<T> controller;
    private final List<Controller<T>> pool;

    public Balancer(Controller<T> controller, List<Controller<T>> pool) {
        this.controller = controller;
        this.pool = pool;
    }

    public <R> PatternPair<T, R> next() {
        Rejected rejected = controller.acquirePermitOrGetRejectedReason();
        if (rejected != null) {
            long nanoTime = System.nanoTime();
            controller.getActionMetrics().incrementRejectionCount(rejected, nanoTime);
            throw new RejectedException(rejected);
        }

        long nanoTime = System.nanoTime();
        Controller<T> childController = findNext(nanoTime);
        if (childController == null) {
            controller.getActionMetrics().incrementRejectionCount(Rejected.ALL_SERVICES_REJECTED, nanoTime);
            controller.getSemaphore().releasePermit(1);
            throw new RejectedException(Rejected.ALL_SERVICES_REJECTED);
        }
        
        PrecipicePromise<T, R> promise = controller.getPromise(nanoTime);
        return new PatternPair<>(controller, controller.getPromise(nanoTime, promise));
    }

    private Controller<T> findNext(long nanoTime) {
        Controller<T> controller = pool.get(0);
        Rejected rejected = controller.acquirePermitOrGetRejectedReason();
        if (rejected != null) {
            controller.getActionMetrics().incrementRejectionCount(rejected, nanoTime);
        } else {
            return controller;
        }
        return null;
    }

    private static class PatternPair<T extends Enum<T> & Result, R> {
        private final Controller<T> controller;
        private final PrecipicePromise<T, R> promise;

        private PatternPair(Controller<T> controller, PrecipicePromise<T, R> promise) {
            this.controller = controller;
            this.promise = promise;
        }
    }
}
