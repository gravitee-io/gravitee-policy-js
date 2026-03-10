## JavaScript engine

Scripts run with **ECMAScript 2023** (ES14) support. You can use modern syntax: `let`/`const`, arrow functions, template literals, destructuring, optional chaining (`?.`), nullish coalescing (`??`), and more.

Each execution runs in a **sandboxed context** with a **100ms timeout**. The sandbox enforces strict isolation:

- No Java interop (`Java.type()`, `Polyglot.eval()`, etc.)
- No file system, network, or native access
- No thread creation or process execution
- No global state shared between executions

`console.log()` and `console.error()` are available and routed to the gateway logs (SLF4J).

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
