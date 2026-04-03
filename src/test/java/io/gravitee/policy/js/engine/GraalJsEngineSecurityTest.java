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
package io.gravitee.policy.js.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseRequest;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseResponse;
import io.gravitee.policy.js.bindings.JsBase64;
import io.gravitee.policy.js.bindings.JsContext;
import io.gravitee.policy.js.bindings.JsHeaders;
import io.gravitee.policy.js.bindings.JsRequest;
import io.gravitee.policy.js.bindings.JsResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GraalJsEngineSecurityTest {

    private static HttpBaseRequest mockRequest() {
        var request = mock(HttpBaseRequest.class);
        when(request.headers()).thenReturn(HttpHeaders.create().set("X-Test", "value"));
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.version()).thenReturn(HttpVersion.HTTP_1_1);
        when(request.parameters()).thenReturn(new LinkedMultiValueMap<>());
        return request;
    }

    private static HttpBaseResponse mockResponse() {
        var response = mock(HttpBaseResponse.class);
        when(response.headers()).thenReturn(HttpHeaders.create());
        when(response.trailers()).thenReturn(HttpHeaders.create());
        when(response.status()).thenReturn(200);
        return response;
    }

    private static BaseExecutionContext mockContext() {
        var ctx = mock(BaseExecutionContext.class);
        when(ctx.getAttributeNames()).thenReturn(Set.of("gravitee.attribute.api"));
        return ctx;
    }

    private static BaseExecutionContext mockContextWithTemplateVariable(String variable, Object value) {
        var ctx = mock(BaseExecutionContext.class);
        var templateEngine = mock(io.gravitee.el.TemplateEngine.class);
        var templateContext = mock(io.gravitee.el.TemplateContext.class);
        when(ctx.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getTemplateContext()).thenReturn(templateContext);
        when(templateContext.lookupVariable(variable)).thenReturn(value);
        return ctx;
    }

    @Nested
    class MemoryExhaustion {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_handle_massive_array_allocation() {
            // May OOM or timeout depending on heap — both produce PolyglotException
            assertThatThrownBy(() -> GraalJsEngine.eval("var a = new Array(1e9).fill('x');", 200)).isInstanceOf(PolyglotException.class);
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_handle_exponential_string_growth() {
            assertThatThrownBy(() -> GraalJsEngine.eval("var s = 'x'; while(true) { s = s + s; }", 200)).isInstanceOf(
                PolyglotException.class
            );
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_handle_recursive_object_expansion() {
            assertThatThrownBy(() ->
                GraalJsEngine.eval("var arr = []; while(true) { arr.push({a: new Array(10000).fill('x')}); }", 200)
            ).isInstanceOf(PolyglotException.class);
        }
    }

    @Nested
    class RegexDos {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_not_hang_on_catastrophic_backtracking() {
            // TRegex is immune to catastrophic backtracking
            assertThatCode(() -> GraalJsEngine.eval("/(a+)+$/.test('aaaaaaaaaaaaaaaaaaaaaaaaaaaaab');", 200)).doesNotThrowAnyException();
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_not_hang_on_nested_quantifier_regex() {
            assertThatCode(() -> GraalJsEngine.eval("/((a+)*)+$/.test('aaaaaaaaaaaaaaaaaaaaab');", 200)).doesNotThrowAnyException();
        }
    }

    @Nested
    class PrototypePollution {

        @Test
        void should_not_leak_prototype_pollution_between_executions() {
            GraalJsEngine.eval("Object.prototype.polluted = true;", 500);
            assertThatCode(() ->
                GraalJsEngine.eval("if (({}).polluted === true) throw new Error('prototype pollution leaked');", 500)
            ).doesNotThrowAnyException();
        }

        @Test
        void should_block_class_lookup_via_constructor_constructor() {
            assertThatThrownBy(() ->
                GraalJsEngine.eval(
                    "var F = ({}).constructor.constructor;" +
                        "var g = F('return this')();" +
                        "g.java.type('java.lang.Runtime').getRuntime().exec('ls');",
                    500
                )
            ).isInstanceOf(PolyglotException.class);
        }

        @Test
        void should_not_access_java_via_proto_on_bindings() {
            var bindings = Map.<String, Object>of("request", new JsRequest(mockRequest()));
            assertThatCode(() ->
                GraalJsEngine.eval(
                    "var p = request.__proto__; if (p && p.getClass) throw new Error('Java leak via __proto__');",
                    500,
                    bindings,
                    null
                )
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    class SandboxEscape {

        @ParameterizedTest(name = "should block getClass() on {0}")
        @ValueSource(strings = { "request", "response", "context", "headers" })
        void should_block_getClass_on_all_bindings(String binding) {
            var request = new JsRequest(mockRequest());
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("request", request);
            bindings.put("response", new JsResponse(mockResponse()));
            bindings.put("context", new JsContext(mockContext()));
            bindings.put("headers", request.headers());

            assertThatThrownBy(() -> GraalJsEngine.eval(binding + ".getClass()", 500, bindings, null)).isInstanceOf(
                PolyglotException.class
            );
        }

        @Test
        void should_block_reflection_via_headers_chain() {
            var bindings = Map.<String, Object>of("request", new JsRequest(mockRequest()));
            assertThatThrownBy(() ->
                GraalJsEngine.eval("request.headers().getClass().forName('java.lang.Runtime')", 500, bindings, null)
            ).isInstanceOf(PolyglotException.class);
        }

        @Test
        void should_not_expose_non_exported_methods_via_enumeration() {
            var bindings = Map.<String, Object>of("request", new JsRequest(mockRequest()));
            assertThatCode(() ->
                GraalJsEngine.eval(
                    "var props = Object.getOwnPropertyNames(request);" +
                        "props.forEach(function(p) {" +
                        "  if (p === 'getClass' || p === 'notify' || p === 'wait' || p === 'hashCode') " +
                        "    throw new Error('Java method exposed: ' + p);" +
                        "});",
                    500,
                    bindings,
                    null
                )
            ).doesNotThrowAnyException();
        }

        @Test
        void should_block_constructor_access_on_bindings() {
            var bindings = Map.<String, Object>of("Base64", new JsBase64());
            assertThatCode(() ->
                GraalJsEngine.eval(
                    "var c = Base64.constructor;" +
                        "if (c && typeof c === 'function' && c !== Object) {" +
                        "  var instance = new c();" +
                        "  if (instance.getClass) throw new Error('constructor escape');" +
                        "}",
                    500,
                    bindings,
                    null
                )
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    class TimeoutBypass {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_timeout_promise_based_loop() {
            assertThatThrownBy(() ->
                GraalJsEngine.eval("async function spin() { while(true) { await Promise.resolve(); } } spin();", 200)
            ).isInstanceOf(PolyglotException.class);
        }

        @ParameterizedTest(name = "should not have {0}")
        @ValueSource(strings = { "setTimeout", "setInterval" })
        void should_not_have_timer_functions(String fn) {
            assertThatThrownBy(() -> GraalJsEngine.eval(fn + "(function(){}, 0);", 500)).isInstanceOf(PolyglotException.class);
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_timeout_eval_with_infinite_loop() {
            assertThatThrownBy(() -> GraalJsEngine.eval("eval('while(true){}')", 200)).isInstanceOf(PolyglotException.class);
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void should_timeout_function_constructor_loop() {
            assertThatThrownBy(() -> GraalJsEngine.eval("var f = new Function('while(true){}'); f();", 200)).isInstanceOf(
                PolyglotException.class
            );
        }
    }

    @Nested
    class ContextSecurity {

        @Test
        void should_list_context_attributes() {
            var bindings = Map.<String, Object>of("context", new JsContext(mockContext()));
            assertThatCode(() ->
                GraalJsEngine.eval("var names = context.getAttributeNames(); if (!names) throw new Error('null');", 500, bindings, null)
            ).doesNotThrowAnyException();
        }

        @Test
        void should_not_allow_writing_to_dictionaries() {
            var ctx = mockContextWithTemplateVariable("dictionaries", Map.of("env", Map.of("key", "value")));
            var bindings = Map.<String, Object>of("context", new JsContext(ctx));
            assertThatThrownBy(() ->
                GraalJsEngine.eval("context.dictionaries().put('evil', 'injected');", 500, bindings, null)
            ).isInstanceOf(PolyglotException.class);
        }

        @Test
        void should_not_allow_writing_to_properties() {
            var ctx = mockContextWithTemplateVariable("properties", Map.of("key", "value"));
            var bindings = Map.<String, Object>of("context", new JsContext(ctx));
            assertThatThrownBy(() -> GraalJsEngine.eval("context.properties().put('evil', 'injected');", 500, bindings, null)).isInstanceOf(
                PolyglotException.class
            );
        }
    }

    @Nested
    class HeaderInjection {

        @Test
        void should_not_split_headers_via_crlf() {
            var headers = HttpHeaders.create();
            var bindings = Map.<String, Object>of("headers", new JsHeaders(headers));
            GraalJsEngine.eval("headers.set('X-Foo', 'bar\\r\\nX-Injected: evil');", 500, bindings, null);
            // CRLF stays in the value (not split into separate headers) — Vert.x validates on write
            assertThat(headers.get("X-Injected")).isNull();
        }

        @Test
        void should_reject_null_header_name() {
            var bindings = Map.<String, Object>of("headers", new JsHeaders(HttpHeaders.create()));
            assertThatThrownBy(() -> GraalJsEngine.eval("headers.set(null, 'value');", 500, bindings, null)).isInstanceOf(
                PolyglotException.class
            );
        }

        @Test
        void should_handle_empty_header_name() {
            var headers = HttpHeaders.create();
            var bindings = Map.<String, Object>of("headers", new JsHeaders(headers));
            assertThatCode(() -> GraalJsEngine.eval("headers.set('', 'value');", 500, bindings, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class ContentEdgeCases {

        @Test
        void should_handle_null_content() {
            var request = new JsRequest(mockRequest());
            var bindings = Map.<String, Object>of("request", request);
            GraalJsEngine.eval("request.content(null);", 500, bindings, null);
            assertThat(request.content()).isNull();
        }

        @Test
        void should_handle_empty_content() {
            var request = new JsRequest(mockRequest());
            var bindings = Map.<String, Object>of("request", request);
            GraalJsEngine.eval("request.content('');", 500, bindings, null);
            assertThat(request.content()).isEmpty();
        }

        @Test
        void should_handle_unicode_content() {
            var request = new JsRequest(mockRequest());
            var bindings = Map.<String, Object>of("request", request);
            GraalJsEngine.eval("request.content('héllo 🚀 中文 العربية');", 500, bindings, null);
            assertThat(request.content()).isEqualTo("héllo 🚀 中文 العربية");
        }

        @Test
        void should_return_null_contentAsBase64_when_no_content() {
            var bindings = Map.<String, Object>of("request", new JsRequest(mockRequest()));
            assertThatCode(() ->
                GraalJsEngine.eval("if (request.contentAsBase64() !== null) throw new Error('expected null');", 500, bindings, null)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    class EvalAndFunctionConstructor {

        @Test
        void should_confine_function_constructor_to_sandbox() {
            assertThatCode(() ->
                GraalJsEngine.eval("var f = new Function('return 42'); if (f() !== 42) throw new Error('broken');", 500)
            ).doesNotThrowAnyException();
        }

        @Test
        void should_block_class_lookup_via_function_constructor_global() {
            // new Function('return this')() exposes a global with java namespace, but class lookup is denied
            assertThatThrownBy(() ->
                GraalJsEngine.eval("var g = new Function('return this')(); g.java.type('java.lang.Runtime').getRuntime().exec('ls');", 500)
            ).isInstanceOf(PolyglotException.class);
        }

        @Test
        void should_block_class_lookup_via_indirect_eval_global() {
            assertThatThrownBy(() ->
                GraalJsEngine.eval("var g = (0, eval)('this'); g.java.type('java.lang.Runtime').getRuntime().exec('ls');", 500)
            ).isInstanceOf(PolyglotException.class);
        }
    }

    @Nested
    class ModuleImport {

        @Test
        void should_not_have_require() {
            assertThatThrownBy(() -> GraalJsEngine.eval("require('fs');", 500)).isInstanceOf(PolyglotException.class);
        }

        @ParameterizedTest(name = "should not have {0} global")
        @ValueSource(strings = { "process", "Deno", "Bun" })
        void should_not_have_runtime_globals(String global) {
            assertThatCode(() ->
                GraalJsEngine.eval("if (typeof " + global + " !== 'undefined') throw new Error('" + global + " exists');", 500)
            ).doesNotThrowAnyException();
        }
    }
}
