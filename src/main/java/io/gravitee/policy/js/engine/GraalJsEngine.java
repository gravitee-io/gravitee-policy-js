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

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;

public class GraalJsEngine {

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "graaljs-timeout-watchdog");
        t.setDaemon(true);
        return t;
    });

    public static final long DEFAULT_TIMEOUT_MS = 100;

    private static final HostAccess HOST_ACCESS = HostAccess.newBuilder()
        .allowAccessAnnotatedBy(HostAccess.Export.class)
        .allowListAccess(true)
        .allowMapAccess(true)
        .build();

    public static void eval(String script, Map<String, Object> bindings, Logger logger) {
        eval(script, DEFAULT_TIMEOUT_MS, bindings, logger);
    }

    static void eval(String script, long timeoutMs) {
        eval(script, timeoutMs, null, null);
    }

    public static void eval(String script, long timeoutMs, Map<String, Object> bindings, Logger logger) {
        try (var context = createSandboxedContext(logger)) {
            if (bindings != null) {
                var jsBindings = context.getBindings("js");
                bindings.forEach(jsBindings::putMember);
            }
            var timeout = TIMEOUT_SCHEDULER.schedule(() -> context.close(true), timeoutMs, TimeUnit.MILLISECONDS);
            try {
                context.eval("js", script);
            } finally {
                timeout.cancel(false);
            }
        }
    }

    private static Context createSandboxedContext(Logger logger) {
        var builder = Context.newBuilder("js")
            .allowHostAccess(HOST_ACCESS)
            .allowHostClassLookup(className -> false)
            .allowIO(IOAccess.NONE)
            .allowNativeAccess(false)
            .allowCreateThread(false)
            .allowCreateProcess(false);

        if (logger != null) {
            builder.out(new Slf4jOutputStream(logger));
            builder.err(Slf4jOutputStream.error(logger));
        } else {
            builder.out(OutputStream.nullOutputStream());
            builder.err(OutputStream.nullOutputStream());
        }

        return builder.build();
    }
}
