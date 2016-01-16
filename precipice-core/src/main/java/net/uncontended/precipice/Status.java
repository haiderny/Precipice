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

package net.uncontended.precipice;

public enum Status implements Result {
    SUCCESS(false, true),
    ERROR(true, true),
    TIMEOUT(true, true),
    CANCELLED(false),
    PENDING(false);

    private final boolean isFailed;
    private final boolean trackLatency;

    Status(boolean isFailed) {
        this(isFailed, false);
    }

    Status(boolean isFailed, boolean trackLatency) {
        this.isFailed = isFailed;
        this.trackLatency = trackLatency;
    }

    @Override
    public boolean isFailure() {
        return isFailed;
    }

    @Override
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    @Override
    public boolean trackLatency() {
        return trackLatency;
    }

    @Override
    public boolean trackMetrics() {
        return true;
    }
}
