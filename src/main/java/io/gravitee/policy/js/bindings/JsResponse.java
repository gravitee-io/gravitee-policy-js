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

import io.gravitee.gateway.reactive.api.context.http.HttpBaseResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.graalvm.polyglot.HostAccess;

public class JsResponse {

    private final HttpBaseResponse response;
    private final JsHeaders headers;
    private final JsHeaders trailers;

    public JsResponse(HttpBaseResponse response) {
        this.response = response;
        this.headers = new JsHeaders(response.headers());
        this.trailers = new JsHeaders(response.trailers());
    }

    @HostAccess.Export
    public int status() {
        return response.status();
    }

    @HostAccess.Export
    public void status(int code) {
        response.status(code);
    }

    @HostAccess.Export
    public String reason() {
        return response.reason();
    }

    @HostAccess.Export
    public void reason(String reason) {
        response.reason(reason);
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
    public JsHeaders trailers() {
        return trailers;
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
