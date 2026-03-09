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

import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.gateway.api.http.HttpHeaders;
import org.junit.jupiter.api.Test;

class JsHeadersTest {

    @Test
    void should_get_header() {
        var headers = HttpHeaders.create().set("X-Test", "value");
        var js = new JsHeaders(headers);
        assertThat(js.get("X-Test")).isEqualTo("value");
    }

    @Test
    void should_get_all_values() {
        var headers = HttpHeaders.create().add("X-Multi", "a").add("X-Multi", "b");
        var js = new JsHeaders(headers);
        assertThat(js.getAll("X-Multi")).containsExactly("a", "b");
    }

    @Test
    void should_set_header() {
        var headers = HttpHeaders.create();
        var js = new JsHeaders(headers);
        js.set("X-New", "val");
        assertThat(headers.get("X-New")).isEqualTo("val");
    }

    @Test
    void should_add_header() {
        var headers = HttpHeaders.create().set("X-Key", "a");
        var js = new JsHeaders(headers);
        js.add("X-Key", "b");
        assertThat(headers.getAll("X-Key")).containsExactly("a", "b");
    }

    @Test
    void should_remove_header() {
        var headers = HttpHeaders.create().set("X-Gone", "bye");
        var js = new JsHeaders(headers);
        js.remove("X-Gone");
        assertThat(headers.contains("X-Gone")).isFalse();
    }

    @Test
    void should_report_contains() {
        var headers = HttpHeaders.create().set("X-Present", "yes");
        var js = new JsHeaders(headers);
        assertThat(js.contains("X-Present")).isTrue();
        assertThat(js.contains("X-Missing")).isFalse();
    }

    @Test
    void should_return_names() {
        var headers = HttpHeaders.create().set("A", "1").set("B", "2");
        var js = new JsHeaders(headers);
        assertThat(js.names()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void should_report_size_and_empty() {
        var empty = new JsHeaders(HttpHeaders.create());
        assertThat(empty.size()).isZero();
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new JsHeaders(HttpHeaders.create().set("X", "1"));
        assertThat(nonEmpty.size()).isEqualTo(1);
        assertThat(nonEmpty.isEmpty()).isFalse();
    }
}
