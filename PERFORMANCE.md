# Performance: GraalJS vs Nashorn

We benchmarked the JS engines directly (context creation + eval), not the full policy through the gateway. Real latency also depends on RxJava scheduling, bindings, serialization and network.

Numbers below come from Java 21 on macOS aarch64, 50-100 runs after warmup, without the GraalVM JIT compiler (interpreter-only, so these are worst-case numbers).

## Comparison

| | Nashorn (legacy) | GraalJS (this policy) |
|---|---|---|
| Cold start | 164ms | 538ms |
| Warm, shared engine | <1ms | n/a (no shared engine) |
| Warm, new engine/context | ~2.6ms | ~3ms |
| Realistic script w/ bindings | <1ms (shared) | ~3ms |
| Lifecycle | Singleton, shared across requests | New context per execution |
| Isolation | None, scripts can leak state via `globalThis` | Full sandbox per execution |
| Security | `--no-java` + manual binding removal | No IO, no threads, no host access |
| JS version | ES5.1 | ES2023 |

When you compare the same model (new engine/context each time), both sit around ~3ms. Nashorn looks faster in prod (<1ms) because it shares a single engine across requests, but that also means scripts can leak state between requests.

We picked GraalJS because ~2ms extra per request doesn't matter in practice, and we get proper sandboxing, ES2023, and no dependency on Nashorn (deprecated, unmaintained).

## End-to-end (gateway benchmark)

We also ran a load test through the full APIM stack (gateway + policy + backend) to confirm the engine-level numbers hold in practice. Backend is a local echo server with 10ms simulated latency, 1000 requests, concurrency 10, Java 21.

### Header transform (onRequest)

Script: `request.headers().set('X-Js-Policy', 'hello from graaljs')`

| | Avg | p50 | p95 | p99 | Req/s |
|---|---|---|---|---|---|
| Baseline (no policy) | 99.7ms | 82.8ms | 88.2ms | 1106.6ms | 83 |
| GraalJS | 93.0ms | 77.2ms | 83.7ms | 1075.4ms | 87 |
| Nashorn | 103.3ms | 80.5ms | 90.3ms | 1098.8ms | 85 |

### Body transform (readContent + overrideContent)

Script: parse JSON body, add a field, serialize back.

| | Avg | p50 | p95 | p99 | Req/s |
|---|---|---|---|---|---|
| Baseline (no policy) | 117.3ms | 82.1ms | 94.2ms | 1519.2ms | 73 |
| GraalJS | 104.4ms | 78.2ms | 83.5ms | 1124.6ms | 84 |
| Nashorn | 98.6ms | 80.8ms | 89.4ms | 172.5ms | 83 |

All three (baseline, GraalJS, Nashorn) land in the same range. The ~3ms engine overhead is invisible next to gateway routing, serialization, and backend latency. The p99 spikes come from GC pauses and Docker scheduling, not the JS engine.

## Why a new context every time

Creating a context costs about 1ms. We considered pooling contexts but that would share global state between executions, which is the same isolation problem Nashorn has. 1ms is nothing next to a typical backend call.

## Cold start

The first GraalJS eval in the JVM takes about 500ms. After that, internal caches kick in and context creation drops to ~1ms. For a long-running gateway this only happens once, so it doesn't matter.
