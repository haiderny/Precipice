/*
 * Copyright 2015 Timothy Brooks
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

package net.uncontended.precipice.exploratory;

import net.uncontended.precipice.AbstractService;
import net.uncontended.precipice.Service;
import net.uncontended.precipice.ServiceProperties;

import java.util.concurrent.*;

public class ActionService extends AbstractService implements Service {
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public ActionService(String name, ServiceProperties properties) {
        super(name, properties.circuitBreaker(), properties.actionMetrics(), properties.latencyMetrics(),
                properties.semaphore());
    }

    public Object execute(final byte[] buffer) throws Exception {
        acquirePermitOrRejectIfActionNotAllowed();

        Future<String> future = executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return String.valueOf(buffer.clone());
            }
        });
        return future.get();
    }

    @Override
    public void shutdown() {
        isShutdown = true;
    }
}
