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

package net.uncontended.precipice.circuit;

import net.uncontended.precipice.BackPressure;

public interface CircuitBreaker<Rejected extends Enum<Rejected>> extends BackPressure<Rejected> {
    boolean isOpen();

    CircuitBreakerConfig<Rejected> getBreakerConfig();

    void setBreakerConfig(CircuitBreakerConfig<Rejected> breakerConfig);

    void forceOpen();

    void forceClosed();
}
