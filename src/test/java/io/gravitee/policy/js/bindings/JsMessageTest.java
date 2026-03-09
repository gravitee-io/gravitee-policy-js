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
import static org.mockito.Mockito.*;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.message.Message;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsMessageTest {

    private Message message;
    private JsMessage jsMessage;

    @BeforeEach
    void setUp() {
        message = mock(Message.class);
        when(message.id()).thenReturn("msg-1");
        when(message.correlationId()).thenReturn("corr-1");
        when(message.timestamp()).thenReturn(42L);
        when(message.error()).thenReturn(false);
        when(message.headers()).thenReturn(HttpHeaders.create());
        when(message.content()).thenReturn(Buffer.buffer("hello"));
        jsMessage = new JsMessage(message);
    }

    @Test
    void should_expose_identity_and_timestamp() {
        assertThat(jsMessage.id()).isEqualTo("msg-1");
        assertThat(jsMessage.correlationId()).isEqualTo("corr-1");
        assertThat(jsMessage.timestamp()).isEqualTo(42L);
        assertThat(jsMessage.error()).isFalse();
    }

    @Test
    void should_expose_content_as_string() {
        assertThat(jsMessage.content()).isEqualTo("hello");
    }

    @Test
    void should_expose_content_as_base64() {
        assertThat(jsMessage.contentAsBase64()).isEqualTo("aGVsbG8=");
    }

    @Test
    void should_return_null_content_when_buffer_is_null() {
        when(message.content()).thenReturn(null);
        var js = new JsMessage(message);
        assertThat(js.content()).isNull();
        assertThat(js.contentAsBase64()).isNull();
    }

    @Test
    void should_set_content() {
        jsMessage.content("new content");
        verify(message).content("new content");
    }

    @Test
    void should_set_error_flag() {
        jsMessage.error(true);
        verify(message).error(true);
    }

    @Test
    void should_set_attribute() {
        jsMessage.setAttribute("key", "val");
        verify(message).attribute("key", "val");
    }

    @Test
    void should_get_attribute() {
        when(message.attribute("key")).thenReturn("val");
        assertThat(jsMessage.getAttribute("key")).isEqualTo("val");
    }

    @Test
    void should_remove_attribute() {
        jsMessage.removeAttribute("key");
        verify(message).removeAttribute("key");
    }

    @Test
    void should_expose_attribute_names() {
        when(message.attributeNames()).thenReturn(Set.of("a", "b"));
        assertThat(jsMessage.attributeNames()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void should_expose_attributes_map() {
        when(message.attributes()).thenReturn(Map.of("a", "1", "b", "2"));
        assertThat(jsMessage.attributes()).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void should_expose_parent_correlation_id() {
        when(message.parentCorrelationId()).thenReturn("parent-1");
        assertThat(jsMessage.parentCorrelationId()).isEqualTo("parent-1");
    }

    @Test
    void should_expose_metadata() {
        when(message.metadata()).thenReturn(Map.of("topic", "events", "partition", 3));
        assertThat(jsMessage.metadata()).containsEntry("topic", "events").containsEntry("partition", 3);
    }
}
