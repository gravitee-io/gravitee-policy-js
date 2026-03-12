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
import io.gravitee.policy.js.bindings.JsBase64;
import io.gravitee.policy.js.bindings.JsContext;
import io.gravitee.policy.js.bindings.JsPolicyResult;
import io.gravitee.policy.js.bindings.JsRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

class GraalJsEngineTest {

    @Test
    void should_eval_simple_script() {
        assertThatCode(() -> GraalJsEngine.eval("var x = 1 + 2;", 500)).doesNotThrowAnyException();
    }

    @Test
    void should_throw_on_syntax_error() {
        assertThatThrownBy(() -> GraalJsEngine.eval("var = ;", 500)).isInstanceOf(PolyglotException.class);
    }

    @Test
    void should_throw_on_runtime_error() {
        assertThatThrownBy(() -> GraalJsEngine.eval("throw new Error('boom')", 500)).isInstanceOf(PolyglotException.class);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void should_cancel_infinite_loop() {
        assertThatThrownBy(() -> GraalJsEngine.eval("while(true) {}", 100))
            .isInstanceOf(PolyglotException.class)
            .satisfies(e -> assertThatCode(() -> ((PolyglotException) e).isCancelled()).doesNotThrowAnyException());
    }

    @Test
    void should_allow_standard_js_eval() {
        assertThatCode(() -> GraalJsEngine.eval("eval('1 + 1')", 500)).doesNotThrowAnyException();
    }

    static Stream<Arguments> blockedScripts() {
        return Stream.of(
            Arguments.of("Java.type('java.lang.System').exit(0)", "Java.type System"),
            Arguments.of("Java.type('java.io.File')", "Java.type File"),
            Arguments.of("java.lang.Runtime.getRuntime().exec('ls')", "Runtime.exec"),
            Arguments.of("new java.lang.ProcessBuilder(['ls'])", "ProcessBuilder"),
            Arguments.of("Polyglot.eval('java', '1+1')", "Polyglot.eval"),
            Arguments.of("Java.type('java.net.URL')", "URL network access"),
            Arguments.of("Java.type('java.net.Socket')", "Socket network access"),
            Arguments.of("Java.type('java.lang.Class').forName('java.lang.System')", "Reflection"),
            Arguments.of("Java.type('java.lang.ClassLoader')", "ClassLoader"),
            Arguments.of("Java.type('java.lang.System').getenv()", "Environment variables"),
            Arguments.of("Java.type('java.lang.Thread').sleep(1000)", "Thread manipulation")
        );
    }

    @Test
    void should_block_stack_overflow() {
        assertThatThrownBy(() -> GraalJsEngine.eval("function f() { f(); } f();", 500)).isInstanceOf(PolyglotException.class);
    }

    @ParameterizedTest(name = "should block: {1}")
    @MethodSource("blockedScripts")
    void should_block_host_access(String script, String label) {
        assertThatThrownBy(() -> GraalJsEngine.eval(script, 500)).isInstanceOf(PolyglotException.class);
    }

    @Test
    void should_isolate_executions() {
        GraalJsEngine.eval("var leaked = 'secret';", 500);
        assertThatCode(() ->
            GraalJsEngine.eval("if (typeof leaked !== 'undefined') throw new Error('leak');", 500)
        ).doesNotThrowAnyException();
    }

    @Test
    void should_not_leak_global_state_between_executions() {
        GraalJsEngine.eval("globalThis.poisoned = true;", 500);
        assertThatCode(() ->
            GraalJsEngine.eval("if (globalThis.poisoned) throw new Error('global state leaked');", 500)
        ).doesNotThrowAnyException();
    }

    @Test
    void should_read_request_header_from_bindings() {
        var request = mock(HttpBaseRequest.class);
        when(request.headers()).thenReturn(HttpHeaders.create().set("X-Test", "value"));
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.version()).thenReturn(HttpVersion.HTTP_1_1);
        when(request.parameters()).thenReturn(new LinkedMultiValueMap<>());

        var bindings = Map.<String, Object>of("request", new JsRequest(request));
        assertThatCode(() ->
            GraalJsEngine.eval("if (request.headers().get('X-Test') !== 'value') throw new Error('wrong');", 500, bindings, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void should_write_context_attribute() {
        var ctx = mock(BaseExecutionContext.class);
        var bindings = Map.<String, Object>of("context", new JsContext(ctx));
        GraalJsEngine.eval("context.setAttribute('foo', 'bar')", 500, bindings, null);
        verify(ctx).setAttribute("foo", "bar");
    }

    @Test
    void should_read_dictionaries_from_context() {
        var ctx = mock(BaseExecutionContext.class);
        var templateEngine = mock(io.gravitee.el.TemplateEngine.class);
        var templateContext = mock(io.gravitee.el.TemplateContext.class);
        when(ctx.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getTemplateContext()).thenReturn(templateContext);
        when(templateContext.lookupVariable("dictionaries")).thenReturn(Map.of("env", "prod"));

        var bindings = Map.<String, Object>of("context", new JsContext(ctx));
        assertThatCode(() ->
            GraalJsEngine.eval("if (context.dictionaries().get('env') !== 'prod') throw new Error('wrong');", 500, bindings, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void should_read_properties_from_context() {
        var ctx = mock(BaseExecutionContext.class);
        var templateEngine = mock(io.gravitee.el.TemplateEngine.class);
        var templateContext = mock(io.gravitee.el.TemplateContext.class);
        when(ctx.getTemplateEngine()).thenReturn(templateEngine);
        when(templateEngine.getTemplateContext()).thenReturn(templateContext);
        when(templateContext.lookupVariable("properties")).thenReturn(Map.of("api.version", "2.0"));

        var bindings = Map.<String, Object>of("context", new JsContext(ctx));
        assertThatCode(() ->
            GraalJsEngine.eval("if (context.properties().get('api.version') !== '2.0') throw new Error('wrong');", 500, bindings, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void should_return_empty_map_when_template_engine_is_null() {
        var ctx = mock(BaseExecutionContext.class);
        when(ctx.getTemplateEngine()).thenReturn(null);

        var bindings = Map.<String, Object>of("context", new JsContext(ctx));
        assertThatCode(() ->
            GraalJsEngine.eval(
                "var d = context.dictionaries(); if (Object.keys(d).length !== 0) throw new Error('expected empty');",
                500,
                bindings,
                null
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void should_block_getClass_on_wrapper() {
        var bindings = Map.<String, Object>of("Base64", new JsBase64());
        assertThatThrownBy(() -> GraalJsEngine.eval("Base64.getClass()", 500, bindings, null)).isInstanceOf(PolyglotException.class);
    }

    @Test
    void should_encode_decode_base64_in_js() {
        var bindings = Map.<String, Object>of("Base64", new JsBase64());
        assertThatCode(() ->
            GraalJsEngine.eval(
                "var encoded = Base64.encode('hello');" + "if (Base64.decode(encoded) !== 'hello') throw new Error('round-trip failed');",
                500,
                bindings,
                null
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void should_resolve_result_fail_overload() {
        var result = new JsPolicyResult();
        var bindings = new java.util.LinkedHashMap<String, Object>();
        bindings.put("result", result);
        bindings.put("State", Map.of("SUCCESS", JsPolicyResult.State.SUCCESS, "FAILURE", JsPolicyResult.State.FAILURE));
        GraalJsEngine.eval("result.fail(401, 'No token', 'UNAUTH');", 500, bindings, null);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getError()).isEqualTo("No token");
        assertThat(result.getKey()).isEqualTo("UNAUTH");
    }

    @Test
    void should_route_console_log_to_slf4j() {
        var logger = mock(Logger.class);
        GraalJsEngine.eval("console.log('hello from JS')", 500, Map.of(), logger);
        verify(logger).info("{}", "hello from JS");
    }

    @Test
    void should_route_console_error_to_slf4j() {
        var logger = mock(Logger.class);
        GraalJsEngine.eval("console.error('oops')", 500, Map.of(), logger);
        verify(logger).error("{}", "oops");
    }

    @Test
    void should_handle_multibyte_utf8_in_console_log() {
        var logger = mock(Logger.class);
        GraalJsEngine.eval("console.log('héllo 🚀')", 500, Map.of(), logger);
        verify(logger).info("{}", "héllo 🚀");
    }
}
