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

import io.gravitee.gateway.reactive.api.message.Message;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.HostAccess;

public class JsMessage {

    private final Message message;
    private final JsHeaders headers;

    public JsMessage(Message message) {
        this.message = message;
        this.headers = new JsHeaders(message.headers());
    }

    @HostAccess.Export
    public String id() {
        return message.id();
    }

    @HostAccess.Export
    public String correlationId() {
        return message.correlationId();
    }

    @HostAccess.Export
    public String parentCorrelationId() {
        return message.parentCorrelationId();
    }

    @HostAccess.Export
    public long timestamp() {
        return message.timestamp();
    }

    @HostAccess.Export
    public boolean error() {
        return message.error();
    }

    @HostAccess.Export
    public void error(boolean error) {
        message.error(error);
    }

    @HostAccess.Export
    public JsHeaders headers() {
        return headers;
    }

    @HostAccess.Export
    public String header(String name) {
        return headers.get(name);
    }

    @HostAccess.Export
    public String content() {
        var buf = message.content();
        return buf != null ? buf.toString() : null;
    }

    @HostAccess.Export
    public String contentAsBase64() {
        var buf = message.content();
        if (buf == null) return null;
        return Base64.getEncoder().encodeToString(buf.getBytes());
    }

    @HostAccess.Export
    public void content(String content) {
        message.content(content);
    }

    @HostAccess.Export
    public Object getAttribute(String name) {
        return message.attribute(name);
    }

    @HostAccess.Export
    public void setAttribute(String name, Object value) {
        message.attribute(name, value);
    }

    @HostAccess.Export
    public void removeAttribute(String name) {
        message.removeAttribute(name);
    }

    @HostAccess.Export
    public Set<String> attributeNames() {
        return message.attributeNames();
    }

    @HostAccess.Export
    public Map<String, Object> attributes() {
        return message.attributes();
    }

    @HostAccess.Export
    public Map<String, Object> metadata() {
        return message.metadata();
    }
}
