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

import net.uncontended.precipice.MultiService;
import net.uncontended.precipice.RejectedActionException;
import net.uncontended.precipice.RejectionReason;
import net.uncontended.precipice.RunService;

import java.util.Map;

public class RunLoadBalancer<C> implements RunPattern<C> {

    private final RunService[] services;
    private final C[] contexts;
    private final LoadBalancerStrategy strategy;

    @SuppressWarnings("unchecked")
    public RunLoadBalancer(Map<? extends RunService, C> executorToContext, LoadBalancerStrategy strategy) {
        if (executorToContext.size() == 0) {
            throw new IllegalArgumentException("Cannot create load balancer with 0 Services.");
        }

        this.strategy = strategy;
        services = new MultiService[executorToContext.size()];
        contexts = (C[]) new Object[executorToContext.size()];
        int i = 0;
        for (Map.Entry<? extends RunService, C> entry : executorToContext.entrySet()) {
            services[i] = entry.getKey();
            contexts[i] = entry.getValue();
            ++i;
        }
    }

    public RunLoadBalancer(RunService[] services, C[] contexts, LoadBalancerStrategy strategy) {
        this.strategy = strategy;
        this.services = services;
        this.contexts = contexts;
    }

    @Override
    public <T> T run(final ResilientPatternAction<T, C> action) throws Exception {
        final int firstServiceToTry = strategy.nextExecutorIndex();
        ResilientActionWithContext<T, C> actionWithContext = new ResilientActionWithContext<>(action);

        int j = 0;
        int serviceCount = services.length;
        while (true) {
            try {
                int serviceIndex = (firstServiceToTry + j) % serviceCount;
                actionWithContext.context = contexts[serviceIndex];
                return services[serviceIndex].run(actionWithContext);
            } catch (RejectedActionException e) {
                ++j;
                if (j == serviceCount) {
                    throw new RejectedActionException(RejectionReason.ALL_SERVICES_REJECTED);
                }
            }
        }
    }


    @Override
    public void shutdown() {
        for (RunService e : services) {
            e.shutdown();
        }
    }
}