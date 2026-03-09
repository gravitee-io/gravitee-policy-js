/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.js.bindings;

import io.gravitee.gateway.api.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.HostAccess;

public class JsHeaders {

    private final HttpHeaders headers;

    public JsHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    @HostAccess.Export
    public String get(String name) {
        return headers.get(name);
    }

    @HostAccess.Export
    public List<String> getAll(String name) {
        return headers.getAll(name);
    }

    @HostAccess.Export
    public JsHeaders set(String name, String value) {
        headers.set(name, value);
        return this;
    }

    @HostAccess.Export
    public JsHeaders add(String name, String value) {
        headers.add(name, value);
        return this;
    }

    @HostAccess.Export
    public void remove(String name) {
        headers.remove(name);
    }

    @HostAccess.Export
    public boolean contains(String name) {
        return headers.contains(name);
    }

    @HostAccess.Export
    public Set<String> names() {
        return headers.names();
    }

    @HostAccess.Export
    public int size() {
        return headers.size();
    }

    @HostAccess.Export
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @HostAccess.Export
    public Map<String, String> toSingleValueMap() {
        return headers.toSingleValueMap();
    }

    @HostAccess.Export
    public Map<String, List<String>> toListValuesMap() {
        return headers.toListValuesMap();
    }
}
