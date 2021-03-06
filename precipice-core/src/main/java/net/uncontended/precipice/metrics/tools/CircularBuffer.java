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

package net.uncontended.precipice.metrics.tools;

import net.uncontended.precipice.metrics.IntervalIterator;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class CircularBuffer<T> {

    private final AtomicReferenceArray<Slot<T>> buffer;
    private final int mask;
    private final int totalSlots;
    private final long nanosPerSlot;
    private final long startNanos;

    public CircularBuffer(int slotsToTrack, long nanosPerSlot) {
        this(slotsToTrack, nanosPerSlot, System.nanoTime());
    }

    public CircularBuffer(int slotsToTrack, long nanosPerSlot, long startNanos) {
        validateSlotSize(nanosPerSlot);

        this.nanosPerSlot = nanosPerSlot;
        this.startNanos = startNanos;
        this.totalSlots = slotsToTrack;

        int arraySlot = nextPositivePowerOfTwo(slotsToTrack);
        this.mask = arraySlot - 1;
        this.buffer = new AtomicReferenceArray<>(arraySlot);

        for (int i = 0; i < arraySlot; ++i) {
            this.buffer.set(i, new Slot<T>(null, startNanos, startNanos));
        }
    }

    public T getSlot(long nanoTime) {
        long absoluteSlot = currentAbsoluteSlot(nanoTime);
        int relativeSlot = toRelative(absoluteSlot);
        Slot<T> slot = buffer.get(relativeSlot);

        if (slot.endNanos - nanoTime > 0 && nanoTime - slot.startNanos >= 0) {
            return slot.object;
        } else {
            return null;
        }
    }

    public T putOrGet(long nanoTime, T object) {
        long absoluteSlot = currentAbsoluteSlot(nanoTime);
        int relativeSlot = toRelative(absoluteSlot);

        for (; ; ) {
            Slot<T> slot = buffer.get(relativeSlot);
            if (nanoTime - slot.startNanos >= 0) {
                if (slot.endNanos - nanoTime > 0) {
                    return slot.object;
                } else {
                    long startNanos = this.startNanos + absoluteSlot * nanosPerSlot;
                    long endNanos = startNanos + nanosPerSlot;
                    Slot<T> newSlot = new Slot<>(object, startNanos, endNanos);
                    if (buffer.compareAndSet(relativeSlot, slot, newSlot)) {
                        return newSlot.object;
                    }
                }
            } else {
                return object;
            }
        }
    }

    public IntervalIterator<T> intervals(long nanoTime, T dead) {
        Intervals intervals = new Intervals(dead);
        intervals.reset(nanoTime);
        return intervals;
    }

    private int toRelative(long absoluteSlot) {
        return (int) (absoluteSlot & mask);
    }

    private long currentAbsoluteSlot(long nanoTime) {
        return (nanoTime - startNanos) / nanosPerSlot;
    }

    private static void validateSlotSize(long nanosPerSlot) {
        if (nanosPerSlot < 0) {
            String message = "Nanos per slot must be positive. Found: [%s duration]";
            throw new IllegalArgumentException(String.format(message, Integer.MAX_VALUE));
        }
//        if (TimeUnit.MILLISECONDS.toNanos(100) > nanosPerSlot) {
//            throw new IllegalArgumentException(String.format("Too low of resolution: [%s nanoseconds]. 100 " +
//                    "milliseconds is the minimum resolution.", nanosPerSlot));
//        }
    }

    private static int nextPositivePowerOfTwo(int slotsToTrack) {
        return 1 << 32 - Integer.numberOfLeadingZeros(slotsToTrack - 1);
    }

    private static class Slot<T> {
        private final T object;
        private final long startNanos;
        private final long endNanos;

        private Slot(T object, long startNanos, long endNanos) {
            this.object = object;
            this.startNanos = startNanos;
            this.endNanos = endNanos;
        }

        @Override
        public String toString() {
            return "Slot{" +
                    "object=" + object +
                    ", startNanos=" + startNanos +
                    ", endNanos=" + endNanos +
                    '}';
        }
    }

    private class Intervals implements IntervalIterator<T> {

        private final T dead;
        private long nanoTime;
        private long currentInterval;
        private long remainderNanos;

        private Intervals(T dead) {
            this.dead = dead;
        }

        @Override
        public boolean hasNext() {
            long diff = nanoTime - currentInterval;
            return diff >= 0;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            long currentInterval = this.currentInterval;
            this.currentInterval += nanosPerSlot;
            T object = getSlot(currentInterval);
            if (object != null) {
                return object;
            } else {
                return dead;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public long intervalStart() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return -(nanoTime - currentInterval + remainderNanos);
        }

        @Override
        public long intervalEnd() {
            long diff = nanoTime - currentInterval;
            if (diff < 0) {
                throw new NoSuchElementException();
            } else if (diff == 0) {
                return 0;
            }

            return -(nanoTime - currentInterval - nanosPerSlot + remainderNanos);
        }

        public long lastIntervalStart() {
            return -(nanoTime - currentInterval + remainderNanos);
        }

        public long lastIntervalEnd() {
            return -(nanoTime - currentInterval - nanosPerSlot + remainderNanos);
        }

        @Override
        public IntervalIterator<T> limit(long duration, TimeUnit unit) {
            long nanosLimit = unit.toNanos(duration);
            long limitedInterval = nanoTime - nanosLimit + nanosPerSlot;
            if (currentInterval - limitedInterval < 0) {
                currentInterval = currentAbsoluteSlot(limitedInterval) * nanosPerSlot + startNanos + remainderNanos;
            }
            return this;
        }

        @Override
        public IntervalIterator<T> reset(long nanoTime) {
            this.nanoTime = nanoTime;
            this.remainderNanos = (nanoTime - startNanos) % nanosPerSlot;

            currentInterval = nanoTime - (totalSlots - 1) * nanosPerSlot;
            return this;
        }
    }
}
