This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway, with full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.

> **Note:** This policy supports V4 APIs only (Proxy and Message). V2 APIs are not supported.

> **Note:** On older default (Alpine) gateway images, you may receive errors when running this policy. This is resolved in the following patch releases (and later): **4.8.29, 4.9.22, 4.10.17, 4.11.10**. If you're on an earlier image, upgrade to one of these — or use the Debian variant of the gateway image, e.g. `graviteeio/apim-gateway:<version>-debian`.
