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

package net.uncontended.precipice.core.samples.http;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import net.uncontended.precipice.core.ResilientAction;

abstract class ServiceRequest<T> implements ResilientAction<T> {

    protected final Request request;
    protected volatile Response response;

    public ServiceRequest(Request request) {
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }

    public void setResponse(Response response) {
        this.response = response;
    }
}