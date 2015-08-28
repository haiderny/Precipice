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

package net.uncontended.precipice.metrics;

import net.uncontended.precipice.utils.SystemTime;
import org.HdrHistogram.AtomicHistogram;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class SnapshotTest {

    @Mock
    private SystemTime systemTime;
    @Mock
    private AtomicHistogram histogram;

    private DefaultActionMetrics metrics;
    private final long offsetTime = 50L;
    private final long millisResolution = 1000L;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TimeUnit unit = TimeUnit.SECONDS;
        long startTime = 0L;
        long resolution = 1;
        int slotsTracked = 10;

        when(systemTime.nanoTime()).thenReturn(startTime);
        metrics = new DefaultActionMetrics(slotsTracked, resolution, unit, histogram, systemTime);
        setupMetrics();
    }

    @Test
    public void testSnapshot() {
        when(systemTime.nanoTime()).thenReturn((offsetTime + millisResolution * 5) * 1000L * 1000L);
        when(histogram.getMaxValue()).thenReturn(10L);
        when(histogram.getMean()).thenReturn(5D);
        when(histogram.getValueAtPercentile(50.0)).thenReturn(2L);
        when(histogram.getValueAtPercentile(90.0)).thenReturn(7L);
        when(histogram.getValueAtPercentile(99.0)).thenReturn(8L);
        when(histogram.getValueAtPercentile(99.9)).thenReturn(9L);
        when(histogram.getValueAtPercentile(99.99)).thenReturn(9L);
        when(histogram.getValueAtPercentile(99.999)).thenReturn(10L);

        Map<Object, Object> snapshot = Snapshot.generate(metrics.totalMetricCounter(),
                metrics.metricCounterIterator(5, TimeUnit.SECONDS), metrics.latencyHistogram());
        assertEquals(29L, snapshot.get(Snapshot.TOTAL_TOTAL));
        assertEquals(5L, snapshot.get(Snapshot.TOTAL_SUCCESSES));
        assertEquals(4L, snapshot.get(Snapshot.TOTAL_TIMEOUTS));
        assertEquals(6L, snapshot.get(Snapshot.TOTAL_ERRORS));
        assertEquals(8L, snapshot.get(Snapshot.TOTAL_CIRCUIT_OPEN));
        assertEquals(2L, snapshot.get(Snapshot.TOTAL_QUEUE_FULL));
        assertEquals(3L, snapshot.get(Snapshot.TOTAL_MAX_CONCURRENCY));
        assertEquals(1L, snapshot.get(Snapshot.TOTAL_ALL_REJECTED));
        assertEquals(22L, snapshot.get(Snapshot.TOTAL));
        assertEquals(4L, snapshot.get(Snapshot.SUCCESSES));
        assertEquals(3L, snapshot.get(Snapshot.TIMEOUTS));
        assertEquals(5L, snapshot.get(Snapshot.ERRORS));
        assertEquals(7L, snapshot.get(Snapshot.CIRCUIT_OPEN));
        assertEquals(1L, snapshot.get(Snapshot.QUEUE_FULL));
        assertEquals(2L, snapshot.get(Snapshot.MAX_CONCURRENCY));
        assertEquals(0L, snapshot.get(Snapshot.ALL_REJECTED));
        assertEquals(6L, snapshot.get(Snapshot.MAX_1_TOTAL));
        assertEquals(2L, snapshot.get(Snapshot.MAX_1_SUCCESSES));
        assertEquals(1L, snapshot.get(Snapshot.MAX_1_TIMEOUTS));
        assertEquals(3L, snapshot.get(Snapshot.MAX_1_ERRORS));
        assertEquals(3L, snapshot.get(Snapshot.MAX_1_CIRCUIT_OPEN));
        assertEquals(1L, snapshot.get(Snapshot.MAX_1_QUEUE_FULL));
        assertEquals(1L, snapshot.get(Snapshot.MAX_1_MAX_CONCURRENCY));
        assertEquals(0L, snapshot.get(Snapshot.MAX_1_ALL_REJECTED));
        assertEquals(10L, snapshot.get(Snapshot.MAX_2_TOTAL));
        assertEquals(3L, snapshot.get(Snapshot.MAX_2_SUCCESSES));
        assertEquals(2L, snapshot.get(Snapshot.MAX_2_TIMEOUTS));
        assertEquals(4L, snapshot.get(Snapshot.MAX_2_ERRORS));
        assertEquals(4L, snapshot.get(Snapshot.MAX_2_CIRCUIT_OPEN));
        assertEquals(1L, snapshot.get(Snapshot.MAX_2_QUEUE_FULL));
        assertEquals(2L, snapshot.get(Snapshot.MAX_2_MAX_CONCURRENCY));
        assertEquals(0L, snapshot.get(Snapshot.MAX_2_ALL_REJECTED));

        assertEquals(10L, snapshot.get(Snapshot.LATENCY_MAX));
        assertEquals(5D, snapshot.get(Snapshot.LATENCY_MEAN));
        assertEquals(2L, snapshot.get(Snapshot.LATENCY_50));
        assertEquals(7L, snapshot.get(Snapshot.LATENCY_90));
        assertEquals(8L, snapshot.get(Snapshot.LATENCY_99));
        assertEquals(9L, snapshot.get(Snapshot.LATENCY_99_9));
        assertEquals(9L, snapshot.get(Snapshot.LATENCY_99_99));
        assertEquals(10L, snapshot.get(Snapshot.LATENCY_99_999));
    }

    private void setupMetrics() {
        long currentTime = 0;

        currentTime = offsetTime * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.SUCCESS, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.TIMEOUT, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.QUEUE_FULL, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ALL_SERVICES_REJECTED, 1000L, currentTime);

        currentTime = (offsetTime + millisResolution) * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.SUCCESS, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.SUCCESS, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);

        currentTime = (offsetTime + millisResolution * 2) * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.SUCCESS, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.TIMEOUT, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);

        currentTime = (offsetTime + millisResolution * 3) * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.SUCCESS, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.ERROR, 1000L, currentTime);

        currentTime = (offsetTime + millisResolution * 4) * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.TIMEOUT, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.QUEUE_FULL, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 1000L, currentTime);

        currentTime = (offsetTime + millisResolution * 5) * 1000L * 1000L;
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.CIRCUIT_OPEN, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.TIMEOUT, 1000L, currentTime);
        metrics.incrementMetricAndRecordLatency(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 1000L, currentTime);

    }
}