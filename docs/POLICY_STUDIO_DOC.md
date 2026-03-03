## Overview
This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway. It is powered by GraalJS, providing full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key| Parameters |
| --- | ---  |
| JS_EXECUTION_FAILURE| Interrupted with a 500 status. Occurs when the JavaScript script throws an error or exceeds the execution timeout. |


