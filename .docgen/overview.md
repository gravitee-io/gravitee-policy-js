This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway, with full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.

> **Note:** This policy supports V4 APIs only (Proxy and Message). V2 APIs are not supported.

> **Note:** You might receive an error when running this policy on the default Alpine-based gateway image. To resolve, use the Debian variant of the gateway image, e.g. `graviteeio/apim-gateway:latest-debian`. A fix for the default Alpine image is in progress.
