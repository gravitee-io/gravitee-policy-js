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

import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.HostAccess;

public class JsContext {

    private final BaseExecutionContext ctx;

    public JsContext(BaseExecutionContext ctx) {
        this.ctx = ctx;
    }

    @HostAccess.Export
    public Object getAttribute(String name) {
        return ctx.getAttribute(name);
    }

    @HostAccess.Export
    public Object get(String name) {
        return getAttribute(name);
    }

    @HostAccess.Export
    public void setAttribute(String name, Object value) {
        ctx.setAttribute(name, value);
    }

    @HostAccess.Export
    public void set(String name, Object value) {
        setAttribute(name, value);
    }

    @HostAccess.Export
    public void removeAttribute(String name) {
        ctx.removeAttribute(name);
    }

    @HostAccess.Export
    public void remove(String name) {
        removeAttribute(name);
    }

    @HostAccess.Export
    public Set<String> getAttributeNames() {
        return ctx.getAttributeNames();
    }

    @HostAccess.Export
    public Map<String, Object> getAttributes() {
        return ctx.getAttributes();
    }

    @HostAccess.Export
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> dictionaries() {
        var engine = ctx.getTemplateEngine();
        if (engine == null) return Map.of();
        var val = engine.getTemplateContext().lookupVariable("dictionaries");
        return val instanceof Map ? (Map<String, Map<String, String>>) val : Map.of();
    }

    @HostAccess.Export
    @SuppressWarnings("unchecked")
    public Map<String, String> properties() {
        var engine = ctx.getTemplateEngine();
        if (engine == null) return Map.of();
        var val = engine.getTemplateContext().lookupVariable("properties");
        return val instanceof Map ? (Map<String, String>) val : Map.of();
    }
}
