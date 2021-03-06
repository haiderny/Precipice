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

import net.uncontended.precipice.GuardRail;
import net.uncontended.precipice.Precipice;
import net.uncontended.precipice.metrics.counts.WritableCounts;
import net.uncontended.precipice.rejected.Rejected;
import net.uncontended.precipice.result.TimeoutableResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PatternTest {

    @Mock
    private PatternStrategy strategy;
    @Mock
    private Precipice<TimeoutableResult, Rejected> precipice1;
    @Mock
    private Precipice<TimeoutableResult, Rejected> precipice2;
    @Mock
    private Precipice<TimeoutableResult, Rejected> precipice3;
    @Mock
    private GuardRail<TimeoutableResult, Rejected> guardRail1;
    @Mock
    private GuardRail<TimeoutableResult, Rejected> guardRail2;
    @Mock
    private GuardRail<TimeoutableResult, Rejected> guardRail3;

    private Pattern<TimeoutableResult, Precipice<TimeoutableResult, Rejected>> pattern;
    private long nanoTime = 10L;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        List<Precipice<TimeoutableResult, Rejected>> controllables = Arrays.asList(precipice1, precipice2, precipice3);
        pattern = new Pattern<>(controllables, strategy);

        int submissionCount = 2;
        when(strategy.acquireCount()).thenReturn(submissionCount);
        when(precipice1.guardRail()).thenReturn(guardRail1);
        when(precipice2.guardRail()).thenReturn(guardRail2);
        when(precipice3.guardRail()).thenReturn(guardRail3);
    }

    @Test
    public void getAllReturnsAListOfAllTheChildPrecipicesInOrder() {
        List<Precipice<TimeoutableResult, Rejected>> all = pattern.getAllPrecipices();
        assertEquals(3, all.size());
        assertEquals(precipice1, all.get(0));
        assertEquals(precipice2, all.get(1));
        assertEquals(precipice3, all.get(2));
    }

    @Test
    public void getReturnsCorrectPrecipices() {
        Integer[] indices = {0, 1, 2};
        SingleReaderArrayIterable iterable = getIterable(indices);

        when(strategy.nextIndices()).thenReturn(iterable);
        when(guardRail1.acquirePermits(1L, nanoTime)).thenReturn(null);
        when(guardRail2.acquirePermits(1L, nanoTime)).thenReturn(null);
        Sequence<Precipice<TimeoutableResult, Rejected>> all = pattern.getPrecipices(1L, nanoTime);

        List<Precipice<TimeoutableResult, Rejected>> controllableList = new ArrayList<>();
        for (Precipice<TimeoutableResult, Rejected> item : all) {
            controllableList.add(item);
        }

        verifyZeroInteractions(precipice3);
        verifyZeroInteractions(guardRail3);

        assertEquals(2, controllableList.size());
        assertSame(precipice1, controllableList.get(0));
        assertSame(precipice2, controllableList.get(1));
    }

    @Test
    public void getAcquiresPermitsInTheCorrectOrder() {
        Integer[] indices = {2, 0, 1};
        WritableCounts<Rejected> metrics = mock(WritableCounts.class);

        when(strategy.nextIndices()).thenReturn(getIterable(indices));
        when(guardRail3.acquirePermits(1L, nanoTime)).thenReturn(null);
        when(guardRail1.acquirePermits(1L, nanoTime)).thenReturn(Rejected.CIRCUIT_OPEN);
        when(guardRail2.acquirePermits(1L, nanoTime)).thenReturn(null);
        when(guardRail1.getRejectedCounts()).thenReturn(metrics);

        Sequence<Precipice<TimeoutableResult, Rejected>> all = pattern.getPrecipices(1L, nanoTime);

        List<Precipice<TimeoutableResult, Rejected>> controllableList = new ArrayList<>();
        for (Precipice<TimeoutableResult, Rejected> item : all) {
            controllableList.add(item);
        }


        assertEquals(2, controllableList.size());
        assertSame(precipice3, controllableList.get(0));
        assertSame(precipice2, controllableList.get(1));
    }

    @Test
    public void getOnlyReturnsTheNumberOfPrecipicesThatAreAvailable() {
        Integer[] indices = {2, 0, 1};

        when(strategy.nextIndices()).thenReturn(getIterable(indices));
        when(guardRail3.acquirePermits(1L, nanoTime)).thenReturn(Rejected.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        when(guardRail1.acquirePermits(1L, nanoTime)).thenReturn(Rejected.CIRCUIT_OPEN);
        when(guardRail2.acquirePermits(1L, nanoTime)).thenReturn(null);

        Sequence<Precipice<TimeoutableResult, Rejected>> all = pattern.getPrecipices(1L, nanoTime);

        List<Precipice<TimeoutableResult, Rejected>> controllableList = new ArrayList<>();
        for (Precipice<TimeoutableResult, Rejected> item : all) {
            controllableList.add(item);
        }


        assertEquals(1, controllableList.size());
        assertSame(precipice2, controllableList.get(0));
    }

    @Test
    public void threadLocalFactoryMeansIteratorIsReusedAndThreadLocal() throws Exception {
        List<Precipice<TimeoutableResult, Rejected>> controllables = Arrays.asList(precipice1, precipice2, precipice3);

        pattern = new Pattern<>(controllables, strategy, new ThreadLocalSequenceFactory<Precipice<TimeoutableResult, Rejected>>());

        final Integer[] indices = {2, 0, 1};
        Executor executor = Executors.newCachedThreadPool();

        when(strategy.nextIndices()).thenAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return getIterable(indices);
            }
        });
        when(guardRail3.acquirePermits(1L, nanoTime)).thenReturn(null);
        when(guardRail1.acquirePermits(1L, nanoTime)).thenReturn(null);
        when(guardRail2.acquirePermits(1L, nanoTime)).thenReturn(null);

        final Sequence<Precipice<TimeoutableResult, Rejected>> firstSequence = pattern.getPrecipices(1L, nanoTime);
        for (int i = 0; i < 10; ++i) {
            Sequence<Precipice<TimeoutableResult, Rejected>> sequence = pattern.getPrecipices(1L, nanoTime);
            assertSame("Should be the same reference.", firstSequence, sequence);
        }

        final ConcurrentHashMap<Sequence<Precipice<TimeoutableResult, Rejected>>, AtomicInteger> sequenceMap = new ConcurrentHashMap<>();
        final CountDownLatch latch = new CountDownLatch(5);
        final CountDownLatch doneLatch = new CountDownLatch(5);

        for (int i = 0; i < 5; ++i) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Sequence<Precipice<TimeoutableResult, Rejected>> first = pattern.getPrecipices(1L, nanoTime);
                    if (sequenceMap.containsKey(first)) {
                        fail("Set should not already contain this sequence.");
                    }
                    sequenceMap.put(first, new AtomicInteger(0));

                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    for (int i = 0; i < 10; ++i) {
                        Sequence<Precipice<TimeoutableResult, Rejected>> sequence = pattern.getPrecipices(1L, nanoTime);
                        sequenceMap.get(sequence).incrementAndGet();
                        assertSame("Should be the same reference.", first, sequence);
                    }
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();

        assertEquals(5, sequenceMap.size());
        for (AtomicInteger count : sequenceMap.values()) {
            assertEquals(10, count.get());
        }
    }

    private static SingleReaderArrayIterable getIterable(Integer[] indices) {
        SingleReaderArrayIterable iterable = new SingleReaderArrayIterable(indices.length);
        Integer[] indices1 = iterable.getIndices();
        System.arraycopy(indices, 0, indices1, 0, indices.length);
        return iterable;
    }
}
