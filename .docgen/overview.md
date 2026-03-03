This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway. It is powered by GraalJS, providing full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.
