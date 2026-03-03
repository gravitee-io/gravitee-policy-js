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

import io.gravitee.policy.api.PolicyConfiguration;

public class JsPolicyConfiguration implements PolicyConfiguration {

    private String script;
    private boolean readContent;
    private boolean overrideContent;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public boolean isReadContent() {
        return readContent;
    }

    public void setReadContent(boolean readContent) {
        this.readContent = readContent;
    }

    public boolean isOverrideContent() {
        return overrideContent;
    }

    public void setOverrideContent(boolean overrideContent) {
        this.overrideContent = overrideContent;
    }
}
