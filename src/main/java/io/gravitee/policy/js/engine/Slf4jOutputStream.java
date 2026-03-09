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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;

public class Slf4jOutputStream extends OutputStream {

    @FunctionalInterface
    interface LogWriter {
        void log(String format, Object arg);
    }

    private final LogWriter logWriter;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public Slf4jOutputStream(Logger logger) {
        this(logger::info);
    }

    Slf4jOutputStream(LogWriter logWriter) {
        this.logWriter = logWriter;
    }

    public static Slf4jOutputStream error(Logger logger) {
        return new Slf4jOutputStream(logger::error);
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flush();
        } else {
            buffer.write(b);
        }
    }

    @Override
    public void flush() {
        if (buffer.size() > 0) {
            logWriter.log("{}", buffer.toString(StandardCharsets.UTF_8));
            buffer.reset();
        }
    }

    @Override
    public void close() {
        flush();
    }
}
