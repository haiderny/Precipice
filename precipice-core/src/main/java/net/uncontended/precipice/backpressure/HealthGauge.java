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

package net.uncontended.precipice.backpressure;

import net.uncontended.precipice.Result;
import net.uncontended.precipice.metrics.HealthSnapshot;
import net.uncontended.precipice.metrics.MetricCounter;

import java.util.concurrent.TimeUnit;

public class HealthGauge<T extends Enum<T> & Result> {

    private final BPCountMetrics<T> metrics;
    private final Class<T> type;

    public HealthGauge(BPCountMetrics<T> metrics) {
        this.metrics = metrics;
        type = metrics.getMetricType();
    }

    public HealthSnapshot getHealth(long timePeriod, TimeUnit timeUnit, long nanoTime) {
        Iterable<MetricCounter<T>> counters = metrics.metricCounterIterable(timePeriod, timeUnit, nanoTime);

        long total = 0;
        long failures = 0;
        for (MetricCounter<T> metricCounter : counters) {
            for (T t : type.getEnumConstants()) {
                long metricCount = metricCounter.getMetricCount(t);
                total += metricCount;

                if (t.isFailure()) {
                    failures += metricCount;
                }
            }
        }
        return new HealthSnapshot(total, 0, failures, 0);
    }
}
