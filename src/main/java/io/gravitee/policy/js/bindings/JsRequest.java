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

import io.gravitee.gateway.reactive.api.context.http.HttpBaseRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.HostAccess;

public class JsRequest {

    private final HttpBaseRequest request;
    private final JsHeaders headers;

    public JsRequest(HttpBaseRequest request) {
        this.request = request;
        this.headers = new JsHeaders(request.headers());
    }

    @HostAccess.Export
    public String id() {
        return request.id();
    }

    @HostAccess.Export
    public String uri() {
        return request.uri();
    }

    @HostAccess.Export
    public String transactionId() {
        return request.transactionId();
    }

    @HostAccess.Export
    public String clientIdentifier() {
        return request.clientIdentifier();
    }

    @HostAccess.Export
    public String host() {
        return request.host();
    }

    @HostAccess.Export
    public String originalHost() {
        return request.originalHost();
    }

    @HostAccess.Export
    public String path() {
        return request.path();
    }

    @HostAccess.Export
    public String pathInfo() {
        return request.pathInfo();
    }

    @HostAccess.Export
    public String contextPath() {
        return request.contextPath();
    }

    @HostAccess.Export
    public String method() {
        return request.method().name();
    }

    @HostAccess.Export
    public String scheme() {
        return request.scheme();
    }

    @HostAccess.Export
    public String version() {
        return request.version().name();
    }

    @HostAccess.Export
    public long timestamp() {
        return request.timestamp();
    }

    @HostAccess.Export
    public String remoteAddress() {
        return request.remoteAddress();
    }

    @HostAccess.Export
    public String localAddress() {
        return request.localAddress();
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
    public Map<String, List<String>> parameters() {
        return Map.copyOf(request.parameters());
    }

    @HostAccess.Export
    public String parameter(String name) {
        var values = request.parameters().get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    @HostAccess.Export
    public Map<String, List<String>> pathParameters() {
        return Map.copyOf(request.pathParameters());
    }

    @HostAccess.Export
    public String pathParameter(String name) {
        var values = request.pathParameters().get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private String content;

    @HostAccess.Export
    public String content() {
        return content;
    }

    @HostAccess.Export
    public String contentAsBase64() {
        if (content == null) return null;
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    @HostAccess.Export
    public void content(String content) {
        this.content = content;
    }
}
