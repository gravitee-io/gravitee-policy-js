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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;

public class GraalJsEngine {

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "graaljs-timeout-watchdog");
        t.setDaemon(true);
        return t;
    });

    private static final long SCRIPT_TIMEOUT_MS = 100;

    public static void eval(String script) {
        eval(script, SCRIPT_TIMEOUT_MS);
    }

    static void eval(String script, long timeoutMs) {
        try (var context = createSandboxedContext()) {
            var timeout = TIMEOUT_SCHEDULER.schedule(() -> context.close(true), timeoutMs, TimeUnit.MILLISECONDS);
            try {
                context.eval("js", script);
            } finally {
                timeout.cancel(false);
            }
        }
    }

    private static Context createSandboxedContext() {
        return Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowHostClassLookup(className -> false)
            .allowIO(IOAccess.NONE)
            .allowNativeAccess(false)
            .allowCreateThread(false)
            .allowCreateProcess(false)
            .build();
    }
}
