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

import static io.gravitee.common.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpBaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.node.logging.NodeLoggerFactory;
import io.gravitee.policy.js.bindings.*;
import io.gravitee.policy.js.engine.GraalJsEngine;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.LinkedHashMap;
import java.util.Map;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;

public class JsPolicy implements HttpPolicy {

    private static final Logger log = NodeLoggerFactory.getLogger(JsPolicy.class);
    private static final String ERROR_KEY = "JS_EXECUTION_FAILURE";
    private static final JsBase64 BASE64 = new JsBase64();

    private final JsPolicyConfiguration configuration;

    public JsPolicy(JsPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String id() {
        return "js";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return execute(ctx, Phase.REQUEST);
    }

    @Override
    public Completable onResponse(HttpPlainExecutionContext ctx) {
        return execute(ctx, Phase.RESPONSE);
    }

    @Override
    public Completable onMessageRequest(HttpMessageExecutionContext ctx) {
        return ctx.request().onMessage(message -> runMessageScript(ctx, message));
    }

    @Override
    public Completable onMessageResponse(HttpMessageExecutionContext ctx) {
        return ctx.response().onMessage(message -> runMessageScript(ctx, message));
    }

    private Completable execute(HttpPlainExecutionContext ctx, Phase phase) {
        String script = configuration.getScript();
        if (script == null || script.isBlank()) {
            return Completable.complete();
        }
        if (configuration.isReadContent()) {
            return executeWithBody(ctx, script, phase);
        }
        return executeWithoutBody(ctx, script);
    }

    private Completable executeWithoutBody(HttpPlainExecutionContext ctx, String script) {
        var result = new JsPolicyResult();
        var bindings = buildBindings(ctx, result);
        var logger = ctx.withLogger(log);
        return Completable.fromAction(() -> GraalJsEngine.eval(script, bindings, logger))
            .subscribeOn(Schedulers.io())
            .onErrorResumeNext(e -> ctx.interruptWith(toFailure(ctx, e)))
            .andThen(Completable.defer(() -> checkResult(ctx, result)));
    }

    private Completable executeWithBody(HttpPlainExecutionContext ctx, String script, Phase phase) {
        var result = new JsPolicyResult();
        var jsRequest = new JsRequest(ctx.request());
        var jsResponse = new JsResponse(ctx.response());
        var bindings = buildBindings(ctx, result, jsRequest, jsResponse);
        var logger = ctx.withLogger(log);
        boolean override = configuration.isOverrideContent();
        boolean isRequest = phase == Phase.REQUEST;

        MaybeTransformer<Buffer, Buffer> transformer = upstream ->
            upstream
                .defaultIfEmpty(Buffer.buffer())
                .flatMapMaybe(body -> {
                    if (isRequest) {
                        jsRequest.content(body.toString());
                    } else {
                        jsResponse.content(body.toString());
                    }
                    return Completable.fromAction(() -> GraalJsEngine.eval(script, bindings, logger))
                        .subscribeOn(Schedulers.io())
                        .onErrorResumeNext(e -> ctx.interruptBodyWith(toFailure(ctx, e)).ignoreElement())
                        .andThen(Completable.defer(() -> checkResult(ctx, result)))
                        .andThen(
                            Maybe.fromCallable(() -> {
                                if (!override) return body;
                                String content = isRequest ? jsRequest.content() : jsResponse.content();
                                var headers = isRequest ? ctx.request().headers() : ctx.response().headers();
                                return replaceBody(content, headers);
                            })
                        );
                });

        if (isRequest) {
            return ctx.request().onBody(transformer);
        } else {
            return ctx.response().onBody(transformer);
        }
    }

    private Maybe<Message> runMessageScript(HttpMessageExecutionContext ctx, Message message) {
        String script = configuration.getScript();
        if (script == null || script.isBlank()) {
            return Maybe.just(message);
        }
        var result = new JsPolicyResult();
        var bindings = buildMessageBindings(ctx, message, result);
        var logger = ctx.withLogger(log);
        return Completable.fromAction(() -> GraalJsEngine.eval(script, bindings, logger))
            .subscribeOn(Schedulers.io())
            .onErrorResumeNext(e -> ctx.interruptMessageWith(toFailure(ctx, e)).ignoreElement())
            .andThen(Completable.defer(() -> checkResult(ctx, result)))
            .andThen(Maybe.just(message));
    }

    private Completable checkResult(HttpPlainExecutionContext ctx, JsPolicyResult result) {
        if (result.isFailed()) {
            return ctx.interruptWith(toResultFailure(result));
        }
        return Completable.complete();
    }

    private Completable checkResult(HttpMessageExecutionContext ctx, JsPolicyResult result) {
        if (result.isFailed()) {
            return ctx.interruptMessageWith(toResultFailure(result)).ignoreElement();
        }
        return Completable.complete();
    }

    private ExecutionFailure toResultFailure(JsPolicyResult result) {
        return new ExecutionFailure(result.getCode()).key(result.getKey()).message(result.getError()).contentType(result.getContentType());
    }

    private Map<String, Object> buildBindings(HttpBaseExecutionContext ctx, JsPolicyResult result) {
        return buildBindings(ctx, result, new JsRequest(ctx.request()), new JsResponse(ctx.response()));
    }

    private Map<String, Object> buildBindings(
        HttpBaseExecutionContext ctx,
        JsPolicyResult result,
        JsRequest jsRequest,
        JsResponse jsResponse
    ) {
        var bindings = new LinkedHashMap<String, Object>();
        bindings.put("request", jsRequest);
        bindings.put("response", jsResponse);
        bindings.put("context", new JsContext(ctx));
        bindings.put("result", result);
        bindings.put("State", Map.of("SUCCESS", JsPolicyResult.State.SUCCESS, "FAILURE", JsPolicyResult.State.FAILURE));
        bindings.put("Base64", BASE64);
        bindings.put("atob", (ProxyExecutable) args -> BASE64.decode(args[0].asString()));
        bindings.put("btoa", (ProxyExecutable) args -> BASE64.encode(args[0].asString()));
        return bindings;
    }

    private Map<String, Object> buildMessageBindings(HttpBaseExecutionContext ctx, Message message, JsPolicyResult result) {
        var bindings = buildBindings(ctx, result);
        bindings.put("message", new JsMessage(message));
        return bindings;
    }

    private static Buffer replaceBody(String content, HttpHeaders headers) {
        Buffer buffer = Buffer.buffer(content != null ? content : "");
        headers.set("Content-Length", String.valueOf(buffer.length()));
        return buffer;
    }

    enum Phase {
        REQUEST,
        RESPONSE,
    }

    private ExecutionFailure toFailure(BaseExecutionContext ctx, Throwable e) {
        String message;
        if (e instanceof PolyglotException pe && pe.isCancelled()) {
            ctx.withLogger(log).warn("JavaScript execution timed out");
            message = "Timeout";
        } else {
            ctx.withLogger(log).warn("JavaScript execution failed: {}", e.getMessage());
            message = "JavaScript execution failed";
        }
        return new ExecutionFailure(INTERNAL_SERVER_ERROR_500).key(ERROR_KEY).message(message);
    }
}
