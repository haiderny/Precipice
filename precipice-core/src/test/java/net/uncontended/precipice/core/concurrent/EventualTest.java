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

package net.uncontended.precipice.core.concurrent;

import net.uncontended.precipice.core.PrecipiceFunction;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class EventualTest {

    @Test
    public void testErrorCallback() throws InterruptedException {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicBoolean isTimedOut = new AtomicBoolean(false);

        Eventual<String> eventual = new Eventual<>();


        IOException exception = new IOException();
        eventual.onError(new PrecipiceFunction<Throwable>() {
            @Override
            public void apply(Throwable argument) {
                error.set(argument);
            }
        });

        eventual.onTimeout(new PrecipiceFunction<Void>() {
            @Override
            public void apply(Void argument) {
                isTimedOut.set(true);
            }
        });

        eventual.onSuccess(new PrecipiceFunction<String>() {
            @Override
            public void apply(String argument) {
                result.set(argument);
            }
        });

        eventual.completeExceptionally(exception);
        eventual.complete("NOO");
        eventual.completeWithTimeout();

        assertSame(exception, error.get());
        assertSame(exception, eventual.error());
        assertNull(result.get());
        assertNull(eventual.result());
        assertFalse(isTimedOut.get());

        try {
            eventual.get();
        } catch (ExecutionException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void testSuccessCallback() throws InterruptedException, ExecutionException {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicBoolean isTimedOut = new AtomicBoolean(false);

        Eventual<String> eventual = new Eventual<>();


        IOException exception = new IOException();
        eventual.onError(new PrecipiceFunction<Throwable>() {
            @Override
            public void apply(Throwable argument) {
                error.set(argument);
            }
        });

        eventual.onTimeout(new PrecipiceFunction<Void>() {
            @Override
            public void apply(Void argument) {
                isTimedOut.set(true);
            }
        });

        eventual.onSuccess(new PrecipiceFunction<String>() {
            @Override
            public void apply(String argument) {
                result.set(argument);
            }
        });

        String stringResult = "YESS";
        eventual.complete(stringResult);
        eventual.completeExceptionally(exception);
        eventual.completeWithTimeout();

        assertSame(stringResult, result.get());
        assertSame(stringResult, eventual.result());
        assertSame(stringResult, eventual.get());
        assertNull(error.get());
        assertNull(eventual.error());
        assertFalse(isTimedOut.get());
    }

    @Test
    public void testTimeoutCallback() throws InterruptedException, ExecutionException {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicBoolean isTimedOut = new AtomicBoolean(false);

        Eventual<String> eventual = new Eventual<>();


        IOException exception = new IOException();
        eventual.onError(new PrecipiceFunction<Throwable>() {
            @Override
            public void apply(Throwable argument) {
                error.set(argument);
            }
        });

        eventual.onTimeout(new PrecipiceFunction<Void>() {
            @Override
            public void apply(Void argument) {
                isTimedOut.set(true);
            }
        });

        eventual.onSuccess(new PrecipiceFunction<String>() {
            @Override
            public void apply(String argument) {
                result.set(argument);
            }
        });

        eventual.completeWithTimeout();
        eventual.completeExceptionally(exception);
        eventual.complete("NOO");

        assertNull(result.get());
        assertNull(eventual.result());
        assertNull(error.get());
        assertNull(eventual.error());
        assertNull(eventual.get());
        assertTrue(isTimedOut.get());
    }
}