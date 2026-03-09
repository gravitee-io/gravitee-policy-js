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

import static io.gravitee.common.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;

import org.graalvm.polyglot.HostAccess;

public class JsPolicyResult {

    public enum State {
        SUCCESS,
        FAILURE,
    }

    private State state = State.SUCCESS;
    private int code = INTERNAL_SERVER_ERROR_500;
    private String error;
    private String key;
    private String contentType;

    @HostAccess.Export
    public State getState() {
        return state;
    }

    @HostAccess.Export
    public void setState(State state) {
        this.state = state;
    }

    @HostAccess.Export
    public int getCode() {
        return code;
    }

    @HostAccess.Export
    public void setCode(int code) {
        this.code = code;
    }

    @HostAccess.Export
    public String getError() {
        return error;
    }

    @HostAccess.Export
    public void setError(String error) {
        this.error = error;
    }

    @HostAccess.Export
    public String getKey() {
        return key;
    }

    @HostAccess.Export
    public void setKey(String key) {
        this.key = key;
    }

    @HostAccess.Export
    public String getContentType() {
        return contentType;
    }

    @HostAccess.Export
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @HostAccess.Export
    public void fail(int code, String error) {
        this.state = State.FAILURE;
        this.code = code;
        this.error = error;
    }

    @HostAccess.Export
    public void fail(int code, String error, String key) {
        fail(code, error);
        this.key = key;
    }

    @HostAccess.Export
    public void fail(int code, String error, String key, String contentType) {
        fail(code, error, key);
        this.contentType = contentType;
    }

    public boolean isFailed() {
        return state == State.FAILURE;
    }
}
