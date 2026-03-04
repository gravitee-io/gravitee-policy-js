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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
            Arguments.of("Polyglot.eval('java', '1+1')", "Polyglot.eval")
        );
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
}
