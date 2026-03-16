
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# JavaScript (New)

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-js/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-js/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-js/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-js.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-js)

## Overview
This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway, with full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.

> **Note:** This policy supports V4 APIs only (Proxy and Message). V2 APIs are not supported.



## Usage
## JavaScript engine

Scripts run with **ECMAScript 2023** (ES14) support. You can use modern syntax: `let`/`const`, arrow functions, template literals, destructuring, optional chaining (`?.`), nullish coalescing (`??`), and more.

Each execution runs in a **sandboxed context** with a **100ms timeout**. The sandbox enforces strict isolation:

- No Java interop (`Java.type()`, `Polyglot.eval()`, etc.)
- No file system, network, or native access
- No thread creation or process execution
- No global state shared between executions

`console.log()` and `console.error()` are available and routed to the gateway logs (SLF4J).

> **Security note:** Scripts have read access to all context attributes, dictionaries, and properties available at execution time. If sensitive data (API keys, tokens, internal identifiers) is present in the execution context, it will be accessible to the script. Make sure your scripts are reviewed and trusted before deployment, especially when using context attributes from upstream policies.

---

## Change the outcome

Use `result.fail()` to interrupt the request with a custom error:

```javascript
if (!request.header('Authorization')) {
    result.fail(401, 'Missing token', 'UNAUTHORIZED');
}
```

The full signature is `result.fail(code, error, [key], [contentType])`:

```javascript
result.fail(400, '{"error":"Bad request"}', 'MY_ERROR_KEY', 'application/json');
```

You can also use the individual setters for more control:

```javascript
result.setState(State.FAILURE);
result.setCode(400);
result.setError('Bad request');
result.setKey('MY_ERROR_KEY');
result.setContentType('application/json');
```

Alternatively, throwing an error interrupts with a `500` status and a `JS_EXECUTION_FAILURE` key:

```javascript
throw new Error('Something went wrong');
```

> **Note:** Property access (`result.state = State.FAILURE`) does not work. You must use `result.setState()` or `result.fail()`.

---

## Override content

Enable both **Read content** and **Override content** in the policy configuration, then use `content()` as getter and setter:

```javascript
var content = JSON.parse(response.content());
content[0].firstname = 'Modified ' + content[0].firstname;
content[0].country = 'US';
response.content(JSON.stringify(content));
```

---

## Context attributes

Read and write execution context attributes shared across policies:

```javascript
context.set('my-key', 'my-value');
var value = context.get('my-key');
context.remove('my-key');
```

The longer `getAttribute()` / `setAttribute()` / `removeAttribute()` forms also work.

---

## Dictionaries and Properties

Access environment-level Dictionaries and API-level Properties:

```javascript
var prop = context.properties()['my-property'];
var dictValue = context.dictionaries()['my-dictionary']['my-key'];
request.headers().set('X-Version', prop);
```

---

## Base64 encoding

Use the standard `btoa()` / `atob()` functions or the `Base64` utility:

```javascript
var encoded = btoa('user:password');
var decoded = atob(encoded);

// Or equivalently:
Base64.encode('user:password');
Base64.decode(encoded);
```

---

## Common examples

### Add headers (request phase)

```javascript
request.headers()
    .set('X-Forwarded-For', request.remoteAddress())
    .set('X-Request-Id', request.id());
```

### Conditional routing with query parameters

```javascript
var env = request.parameter('env');
if (env === 'staging') {
    request.headers().set('X-Backend', 'staging.internal');
}
```

### Read a header shorthand

```javascript
var auth = request.header('Authorization');
var contentType = response.header('Content-Type');
```

### Set Basic Auth

```javascript
request.headers().set('Authorization', 'Basic ' + btoa('user:password'));
```

### Transform a message (message phase)

```javascript
var content = JSON.parse(message.content());
content.processed = true;
message.content(JSON.stringify(content));
message.headers().set('X-Processed', 'true');
```

---

# API Reference

## Request

`request.<method>()`

| Method                 | Return type                 | Description                                                      |
|------------------------|-----------------------------|------------------------------------------------------------------|
| `id()`                 | `String`                    | Request identifier.                                              |
| `transactionId()`      | `String`                    | Unique identifier for the transaction.                           |
| `clientIdentifier()`   | `String`                    | Identifies the client that made the request.                     |
| `uri()`                | `String`                    | The complete request URI.                                        |
| `host()`               | `String`                    | Host from the incoming request.                                  |
| `originalHost()`       | `String`                    | Host as originally received before any rewriting.                |
| `contextPath()`        | `String`                    | API context path.                                                |
| `pathInfo()`           | `String`                    | Path beyond the context path.                                    |
| `path()`               | `String`                    | The full path component of the request URI.                      |
| `method()`             | `String`                    | HTTP method (`"GET"`, `"POST"`, etc.).                           |
| `scheme()`             | `String`                    | `"http"` or `"https"`.                                           |
| `version()`            | `String`                    | Protocol version: `"HTTP_1_0"`, `"HTTP_1_1"`, `"HTTP_2"`.       |
| `timestamp()`          | `long`                      | Epoch timestamp when request was received.                       |
| `remoteAddress()`      | `String`                    | Client IP address.                                               |
| `localAddress()`       | `String`                    | Local server IP address.                                         |
| `header(name)`         | `String`                    | Get a request header value (shorthand for `headers().get(name)`). |
| `headers()`            | Headers object              | HTTP headers (mutable). See [Headers methods](#headers-methods). |
| `parameter(name)`      | `String`                    | Get the first value of a query parameter, or `null`.             |
| `parameters()`         | `Map<String, List<String>>` | All query parameters (read-only).                                |
| `pathParameter(name)`  | `String`                    | Get the first value of a path parameter, or `null`.              |
| `pathParameters()`     | `Map<String, List<String>>` | Parameters extracted from path templates (read-only).            |
| `content()`            | `String`                    | Request body (only when **Read content** is enabled).            |
| `contentAsBase64()`    | `String`                    | Request body as a base64 string.                                 |
| `content(String)`      | void                        | Set new request body (only when **Override content** is enabled). |

---

## Response

`response.<method>()`

| Method              | Return type  | Description                                                       |
|---------------------|--------------|-------------------------------------------------------------------|
| `status()`          | `int`        | Response status code.                                             |
| `status(int)`       | void         | Set response status code.                                         |
| `reason()`          | `String`     | Reason phrase for the status.                                     |
| `reason(String)`    | void         | Set reason phrase.                                                |
| `header(name)`      | `String`     | Get a response header value (shorthand for `headers().get(name)`). |
| `headers()`         | Headers object | HTTP headers (mutable). See [Headers methods](#headers-methods). |
| `trailers()`        | Headers object | HTTP trailers (mutable). See [Headers methods](#headers-methods). |
| `content()`         | `String`     | Response body (only when **Read content** is enabled).            |
| `contentAsBase64()` | `String`     | Response body as a base64 string.                                 |
| `content(String)`   | void         | Set new response body (only when **Override content** is enabled). |

---

## Message

`message.<method>()` — available in message request/response phases only.

| Method                          | Return type           | Description                                                        |
|---------------------------------|-----------------------|--------------------------------------------------------------------|
| `id()`                          | `String`              | Message identifier.                                                |
| `correlationId()`               | `String`              | Correlation ID to track the message.                               |
| `parentCorrelationId()`         | `String`              | Parent correlation ID.                                             |
| `timestamp()`                   | `long`                | Epoch (ms) timestamp.                                              |
| `error()`                       | `boolean`             | Whether the message is an error message.                           |
| `error(boolean)`                | void                  | Set message error flag.                                            |
| `header(name)`                  | `String`              | Get a message header value (shorthand for `headers().get(name)`).  |
| `headers()`                     | Headers object        | Message headers (mutable). See [Headers methods](#headers-methods). |
| `metadata()`                    | `Map<String, Object>` | Message metadata (mutable).                                        |
| `attributes()`                  | `Map<String, Object>` | Message attributes (mutable).                                      |
| `content()`                     | `String`              | Message body as a string.                                          |
| `content(String)`               | void                  | Set message body.                                                  |
| `contentAsBase64()`             | `String`              | Message body as a base64 string.                                   |
| `getAttribute(String)`          | `Object`              | Get a message attribute.                                           |
| `setAttribute(String, Object)`  | void                  | Set a message attribute.                                           |
| `removeAttribute(String)`       | void                  | Remove a message attribute.                                        |
| `attributeNames()`              | `Set<String>`         | All message attribute names.                                       |

---

## Context

`context.<method>()`

| Method               | Return type                        | Description              |
|----------------------|------------------------------------|--------------------------|
| `get(name)`          | `Object`                           | Get a context attribute. |
| `set(name, value)`   | void                               | Set a context attribute. |
| `remove(name)`       | void                               | Remove a context attribute. |
| `getAttributeNames()`| `Set<String>`                      | All attribute names.     |
| `getAttributes()`    | `Map<String, Object>`              | All attributes as a map. |
| `dictionaries()`     | `Map<String, Map<String, String>>` | Environment dictionaries. |
| `properties()`       | `Map<String, String>`              | API properties.          |

The longer `getAttribute(name)` / `setAttribute(name, value)` / `removeAttribute(name)` forms are also supported.

---

## <a id="headers-methods"></a>Headers methods

Applicable to `request.headers()`, `response.headers()`, `response.trailers()`, and `message.headers()`.

`set()` and `add()` return the headers object, so calls can be chained.

| Method               | Arguments          | Return type                 | Description                          |
|----------------------|--------------------|-----------------------------|--------------------------------------|
| `get(name)`          | `String`           | `String`                    | Get first value of a header.         |
| `getAll(name)`       | `String`           | `List<String>`              | Get all values of a header.          |
| `set(name, value)`   | `String`, `String` | Headers                     | Replace header with a single value.  |
| `add(name, value)`   | `String`, `String` | Headers                     | Add a value to a header.             |
| `remove(name)`       | `String`           | void                        | Remove a header.                     |
| `contains(name)`     | `String`           | `boolean`                   | Check if a header exists.            |
| `names()`            |                    | `Set<String>`               | All header names.                    |
| `size()`             |                    | `int`                       | Header count.                        |
| `isEmpty()`          |                    | `boolean`                   | `true` if no headers exist.          |
| `toSingleValueMap()` |                    | `Map<String, String>`       | Headers as single-value map.         |
| `toListValuesMap()`  |                    | `Map<String, List<String>>` | Headers as multi-value map.          |

---

## Result

| Method                             | Description                                            |
|------------------------------------|--------------------------------------------------------|
| `fail(code, error)`               | Interrupt with an HTTP status code and error message.  |
| `fail(code, error, key)`          | Same, with a response template key.                    |
| `fail(code, error, key, contentType)` | Same, with a content type for the error body.      |
| `setState(state)`                 | Set `State.SUCCESS` or `State.FAILURE`.                |
| `setCode(code)`                   | Set the HTTP status code (default: 500).               |
| `setError(message)`               | Set the error message.                                 |
| `setKey(key)`                     | Set the response template key.                         |
| `setContentType(type)`            | Set the error response content type.                   |

---

## Global functions

| Function                | Description                        |
|-------------------------|------------------------------------|
| `btoa(string)`          | Encode a string to Base64.         |
| `atob(string)`          | Decode a Base64 string.            |
| `Base64.encode(string)` | Encode a string to Base64.         |
| `Base64.decode(string)` | Decode a Base64 string.            |
| `console.log(message)`  | Log to gateway logs (INFO level).  |
| `console.error(message)`| Log to gateway logs (ERROR level). |

---

## Migrating from the legacy JavaScript policy

Key differences with the legacy policy:

| Aspect                    | Legacy (`javascript`)      | New (`js`)                                                   |
|---------------------------|----------------------------|--------------------------------------------------------------|
| Headers access            | `request.headers.set(...)` | `request.headers().set(...)`                                 |
| Content read              | `request.content` (property) | `request.content()` (method)                               |
| Content override          | Return value of script     | `request.content('new body')`                                |
| `httpClient`              | Available (deprecated)     | Removed — use the Callout HTTP policy                        |
| `method()` / `version()`  | Java enum                  | String (`"GET"`, `"HTTP_1_1"`)                               |
| Phase-specific scripts    | `onRequestScript`, etc.    | Single `script` field                                        |
| Sandbox                   | Minimal (binding cleanup)  | Strict (no Java access, no I/O, 100ms timeout)               |
| `Base64`                  | Not available              | `Base64.encode()` / `Base64.decode()` / `btoa()` / `atob()` |

### Migration example

**Before (legacy):**
```javascript
var content = JSON.parse(response.content);
content.modified = true;
JSON.stringify(content);
```

**After (new):**
```javascript
var content = JSON.parse(response.content());
content.modified = true;
response.content(JSON.stringify(content));
```




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key| Parameters |
| --- | ---  |
| JS_EXECUTION_FAILURE| Interrupted with a 500 status. Occurs when the JavaScript script throws an error or exceeds the execution timeout. |



## Phases
The `js` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MESSAGE`

### Supported flow phases:

* Request
* Response
* Publish
* Subscribe

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM| Java version |
| --- | --- | ---  |
|1.0.0 and after|4.10.x and after|21 |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Override content<br>`overrideContent`| boolean|  | | Enable to override the content with the value returned by your script.|
| Read content<br>`readContent`| boolean|  | | Enable to read the content of the request or response in your script.|
| Script<br>`script`| string| ✅| `request.headers().set('X-Custom', 'value');`| JavaScript script to evaluate.|




## Examples
*Proxy API With Defaults*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JavaScript (New) example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "JavaScript (New)",
            "enabled": true,
            "policy": "js",
            "configuration":
              {
                "overrideContent": false,
                "readContent": false,
                "script": "request.headers().set('X-Custom', 'value');"
              }
          }
        ]
      }
    ]
  }
}

```

*Proxy API on Request phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JavaScript (New) example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "JavaScript (New)",
            "enabled": true,
            "policy": "js",
            "configuration":
              {
                  "readContent": false,
                  "overrideContent": false,
                  "script": "request.headers().set('X-Js-Policy', 'ok');"
              }
          }
        ]
      }
    ]
  }
}

```
*Proxy API on Response phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JavaScript (New) example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "response": [
          {
            "name": "JavaScript (New)",
            "enabled": true,
            "policy": "js",
            "configuration":
              {
                  "readContent": false,
                  "overrideContent": false,
                  "script": "response.headers().set('X-Js-Policy', 'ok');"
              }
          }
        ]
      }
    ]
  }
}

```
*Proxy API on Response phase - override content*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JavaScript (New) example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "response": [
          {
            "name": "JavaScript (New)",
            "enabled": true,
            "policy": "js",
            "configuration":
              {
                  "readContent": true,
                  "overrideContent": true,
                  "script": "response.content(response.content() + ' appended by JS policy');"
              }
          }
        ]
      }
    ]
  }
}

```
*Message API CRD*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "js-message-api-crd"
spec:
    name: "JavaScript (New) example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        request:
          - name: "JavaScript (New)"
            enabled: true
            policy: "js"
            configuration:
              overrideContent: false
              readContent: false
              script: message.headers().set('X-Js-Policy', 'ok');

```
*Message API CRD - override content*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "js-message-api-crd"
spec:
    name: "JavaScript (New) example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        request:
          - name: "JavaScript (New)"
            enabled: true
            policy: "js"
            configuration:
              overrideContent: true
              readContent: true
              script: message.content(message.content() + ' appended by JS policy');

```


## Changelog

### [1.0.0-alpha.4](https://github.com/gravitee-io/gravitee-policy-js/compare/1.0.0-alpha.3...1.0.0-alpha.4) (2026-03-16)


##### Features

* add configurable script timeout via gravitee.yml ([240fde3](https://github.com/gravitee-io/gravitee-policy-js/commit/240fde3da1a01cce2e1cb7b9888950e9aee32861))

### [1.0.0-alpha.3](https://github.com/gravitee-io/gravitee-policy-js/compare/1.0.0-alpha.2...1.0.0-alpha.3) (2026-03-16)


##### Bug Fixes

* add null guards on atob/btoa with integration tests ([bb7e80c](https://github.com/gravitee-io/gravitee-policy-js/commit/bb7e80c9cfb95b804366b259af0e3a1cabc0f61d))

### [1.0.0-alpha.2](https://github.com/gravitee-io/gravitee-policy-js/compare/1.0.0-alpha.1...1.0.0-alpha.2) (2026-03-11)


##### Bug Fixes

* address PR review feedback ([c499b3c](https://github.com/gravitee-io/gravitee-policy-js/commit/c499b3c02d622e1bd31d40237f7ff4cd2b7dde83))


##### Features

* add JS bindings and context API ([e227e26](https://github.com/gravitee-io/gravitee-policy-js/commit/e227e263b45b66a4ff0a2f780d571b88d87bbb61))

### 1.0.0-alpha.1 (2026-03-05)


##### Bug Fixes

* remove stacktrace from warn log to avoid duplication ([e03b7c2](https://github.com/gravitee-io/gravitee-policy-js/commit/e03b7c250d67238a75459b2a0945f9cb9179fd80))


##### Features

* implement secure GraalJS execution engine ([d80b7f2](https://github.com/gravitee-io/gravitee-policy-js/commit/d80b7f22582fc7ed5a269856e671c8a25c3fe8d1))

### Changelog

