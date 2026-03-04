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
package io.gravitee.policy.js;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JsPolicyTest {

    @Test
    void should_return_js_as_id() {
        var config = new JsPolicyConfiguration();
        config.setScript("1+1");
        assertThat(new JsPolicy(config).id()).isEqualTo("js");
    }

    @Test
    void should_complete_when_script_succeeds() {
        var policy = policyWith("var x = 1 + 2;");
        var ctx = mock(HttpPlainExecutionContext.class);

        policy.onRequest(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors();
    }

    @Test
    void should_fail_with_timeout_on_infinite_loop() {
        var policy = policyWith("while(true) {}");
        var ctx = mock(HttpPlainExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptWith(any())).thenReturn(Completable.complete());

        policy.onRequest(ctx).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptWith(
            argThat(
                failure ->
                    failure.statusCode() == 500 && "Timeout".equals(failure.message()) && "JS_EXECUTION_FAILURE".equals(failure.key())
            )
        );
    }

    @Test
    void should_fail_when_accessing_java_system() {
        var policy = policyWith("Java.type('java.lang.System').exit(0)");
        var ctx = mock(HttpPlainExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptWith(any())).thenReturn(Completable.complete());

        policy.onRequest(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_fail_when_accessing_java_file() {
        var policy = policyWith("Java.type('java.io.File')");
        var ctx = mock(HttpPlainExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptWith(any())).thenReturn(Completable.complete());

        policy.onRequest(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_interrupt_messages_on_failure() {
        var policy = policyWith("throw new Error('boom')");
        var ctx = mock(HttpMessageExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptMessagesWith(any())).thenReturn(Flowable.empty());

        policy.onMessageRequest(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptMessagesWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_complete_on_response() {
        var policy = policyWith("var x = 1;");
        var ctx = mock(HttpPlainExecutionContext.class);

        policy.onResponse(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors();
    }

    @Test
    void should_fail_on_response_with_error() {
        var policy = policyWith("throw new Error('fail')");
        var ctx = mock(HttpPlainExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptWith(any())).thenReturn(Completable.complete());

        policy.onResponse(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_interrupt_message_response_on_failure() {
        var policy = policyWith("throw new Error('boom')");
        var ctx = mock(HttpMessageExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptMessagesWith(any())).thenReturn(Flowable.empty());

        policy.onMessageResponse(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptMessagesWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_fail_on_syntax_error() {
        var policy = policyWith("var = ;");
        var ctx = mock(HttpPlainExecutionContext.class);
        when(ctx.withLogger(any())).thenReturn(LoggerFactory.getLogger(JsPolicyTest.class));
        when(ctx.interruptWith(any())).thenReturn(Completable.complete());

        policy.onRequest(ctx).test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        verify(ctx).interruptWith(argThat(failure -> failure.statusCode() == 500 && "JS_EXECUTION_FAILURE".equals(failure.key())));
    }

    @Test
    void should_complete_when_script_is_null() {
        var policy = policyWith(null);
        var ctx = mock(HttpPlainExecutionContext.class);

        policy.onRequest(ctx).test().assertComplete().assertNoErrors();
        verify(ctx, never()).interruptWith(any());
    }

    @Test
    void should_complete_when_script_is_blank() {
        var policy = policyWith("   ");
        var ctx = mock(HttpPlainExecutionContext.class);

        policy.onRequest(ctx).test().assertComplete().assertNoErrors();
        verify(ctx, never()).interruptWith(any());
    }

    private JsPolicy policyWith(String script) {
        var config = new JsPolicyConfiguration();
        config.setScript(script);
        return new JsPolicy(config);
    }
}
