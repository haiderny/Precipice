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

package net.uncontended.precipice.metrics;

public class SimpleCountMetrics<T extends Enum<T>> implements TotalCountMetrics<T> {

    private final MetricCounter<T> totalCounter;
    private final Class<T> type;

    public SimpleCountMetrics(Class<T> type) {
        this.type = type;
        totalCounter = new MetricCounter<>(type);
    }

    @Override
    public void incrementMetricCount(T metric) {
        totalCounter.incrementMetric(metric);
    }

    @Override
    public void incrementMetricCount(T metric, long nanoTime) {
        totalCounter.incrementMetric(metric);

    }

    @Override
    public long getTotalMetricCount(T metric) {
        return totalCounter.getMetricCount(metric);
    }

    @Override
    public MetricCounter<T> totalCountMetricCounter() {
        return totalCounter;
    }

    @Override
    public Class<T> getMetricType() {
        return type;
    }
}
