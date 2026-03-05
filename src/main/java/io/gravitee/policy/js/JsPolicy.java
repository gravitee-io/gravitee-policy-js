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

import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.node.logging.NodeLoggerFactory;
import io.gravitee.policy.js.engine.GraalJsEngine;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.graalvm.polyglot.PolyglotException;
import org.slf4j.Logger;

public class JsPolicy implements HttpPolicy {

    private static final Logger log = NodeLoggerFactory.getLogger(JsPolicy.class);
    private static final String ERROR_KEY = "JS_EXECUTION_FAILURE";

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
        return executeScript().onErrorResumeNext(e -> ctx.interruptWith(toFailure(ctx, e)));
    }

    @Override
    public Completable onResponse(HttpPlainExecutionContext ctx) {
        return executeScript().onErrorResumeNext(e -> ctx.interruptWith(toFailure(ctx, e)));
    }

    @Override
    public Completable onMessageRequest(HttpMessageExecutionContext ctx) {
        return executeScript().onErrorResumeNext(e -> ctx.interruptMessagesWith(toFailure(ctx, e)).ignoreElements());
    }

    @Override
    public Completable onMessageResponse(HttpMessageExecutionContext ctx) {
        return executeScript().onErrorResumeNext(e -> ctx.interruptMessagesWith(toFailure(ctx, e)).ignoreElements());
    }

    private Completable executeScript() {
        String script = configuration.getScript();
        if (script == null || script.isBlank()) {
            return Completable.complete();
        }
        return Completable.fromAction(() -> GraalJsEngine.eval(script)).subscribeOn(Schedulers.io());
    }

    private ExecutionFailure toFailure(BaseExecutionContext ctx, Throwable e) {
        ctx.withLogger(log).warn("JavaScript execution failed");
        String message = (e instanceof PolyglotException pe && pe.isCancelled()) ? "Timeout" : "JavaScript execution failed";
        return new ExecutionFailure(INTERNAL_SERVER_ERROR_500).key(ERROR_KEY).message(message);
    }
}
