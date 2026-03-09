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

import org.junit.jupiter.api.Test;

class JsBase64Test {

    private final JsBase64 base64 = new JsBase64();

    @Test
    void should_encode() {
        assertThat(base64.encode("hello")).isEqualTo("aGVsbG8=");
    }

    @Test
    void should_decode() {
        assertThat(base64.decode("aGVsbG8=")).isEqualTo("hello");
    }

    @Test
    void should_round_trip() {
        String original = "Gravitee JS Policy! 🚀";
        assertThat(base64.decode(base64.encode(original))).isEqualTo(original);
    }
}
