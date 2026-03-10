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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.get.HttpGetEntrypointConnectorFactory;
import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.MessageStorage;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.PersistentMockEndpointConnectorFactory;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class JsPolicyIntegrationTest {

    private static final JsonObject GIVEN_CONTENT = new JsonObject("{\"message\":\"Hello World!\"}");
    private static final JsonObject EXPECTED_CONTENT = new JsonObject("{\"message\":\"Hello Universe!\"}");

    @GatewayTest
    @Nested
    class HttpProxyTest extends AbstractPolicyTest<JsPolicy, JsPolicyConfiguration> {

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        }

        @Test
        @DeployApi("/apis/v4/api-request-override.json")
        void should_override_request_content(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Phase", equalTo("on-request"))
                    .withRequestBody(equalToJson(EXPECTED_CONTENT.toString()))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-no-override.json")
        void should_not_override_request_content_when_disabled(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Phase", equalTo("on-request"))
                    .withRequestBody(equalToJson(GIVEN_CONTENT.toString()))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-response-override.json")
        void should_override_response_content(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(okJson(GIVEN_CONTENT.toString())));

            client
                .rxRequest(POST, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .doOnSuccess(response -> assertThat(response.headers().names()).contains("X-Phase"))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString(EXPECTED_CONTENT.toString()))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
        }

        @Test
        @DeployApi("/apis/v4/api-request-fail.json")
        void should_interrupt_with_result_fail(HttpClient client) {
            wiremock.stubFor(post("/team").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(401))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-request-headers.json")
        void should_set_headers_with_btoa_and_chaining(HttpClient client) {
            wiremock.stubFor(get("/team").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                getRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Auth", equalTo("Basic dXNlcjpwYXNz"))
                    .withHeader("X-Method", equalTo("GET"))
                    .withHeader("X-Scheme", matching("https?"))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-script-error.json")
        void should_return_500_on_script_error(HttpClient client) {
            wiremock.stubFor(get("/team").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-request-multi-step.json")
        void should_execute_multi_step_script_with_conditional_logic(HttpClient client) {
            wiremock.stubFor(post(urlPathEqualTo("/team")).willReturn(ok("")));

            client
                .rxRequest(POST, "/test?env=staging")
                .flatMap(request -> request.rxSend("{\"name\":\"test\"}"))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                postRequestedFor(urlPathEqualTo("/team"))
                    .withHeader("X-Backend", equalTo("staging.internal"))
                    .withRequestBody(matchingJsonPath("$.backend", equalTo("staging")))
                    .withRequestBody(matchingJsonPath("$.method", equalTo("POST")))
                    .withRequestBody(matchingJsonPath("$.auth", equalTo("dXNlcjpwYXNz")))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-empty-body.json")
        void should_handle_empty_body_with_read_content(HttpClient client) {
            wiremock.stubFor(get("/team").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, getRequestedFor(urlPathEqualTo("/team")).withHeader("X-Body", equalTo("generated")));
        }

        @Test
        @DeployApi("/apis/v4/api-response-conditional.json")
        void should_enrich_successful_response(HttpClient client) {
            wiremock.stubFor(get("/team").willReturn(okJson("{\"data\":\"ok\"}")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .doOnSuccess(response -> assertThat(response.getHeader("X-Enriched")).isEqualTo("true"))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> {
                    var json = new JsonObject(body.toString());
                    assertThat(json.getBoolean("enriched")).isTrue();
                    assertThat(json.getString("data")).isEqualTo("ok");
                })
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
        }

        @Test
        @DeployApi("/apis/v4/api-request-fail-then-throw.json")
        void should_use_error_status_when_script_fails_then_throws(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-request-dictionaries.json")
        void should_expose_dictionaries_and_properties(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                getRequestedFor(urlPathEqualTo("/foobar"))
                    .withHeader("X-Dict-Keys", equalTo("0"))
                    .withHeader("X-Props-Keys", equalTo("0"))
                    .withHeader("X-Dict-Mutable", equalTo("false"))
                    .withHeader("X-Props-Mutable", equalTo("false"))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-context-attrs.json")
        void should_share_context_attributes_between_policies(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(
                1,
                getRequestedFor(urlPathEqualTo("/foobar"))
                    .withHeader("X-My-Key", equalTo("my-value"))
                    .withHeader("X-Other-Key", equalTo("other-value"))
                    .withHeader("X-Removed", equalTo("null"))
            );
        }

        @Test
        @DeployApi("/apis/v4/api-request-content-base64.json")
        void should_expose_content_as_base64(HttpClient client) {
            wiremock.stubFor(post("/foobar").willReturn(ok("")));

            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend("hello"))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, postRequestedFor(urlPathEqualTo("/foobar")).withHeader("X-Body-B64", equalTo("aGVsbG8=")));
        }

        @Test
        @DeployApi("/apis/v4/api-response-status-override.json")
        void should_override_response_status_and_reason(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(201))
                .doOnSuccess(response -> assertThat(response.getHeader("X-Original-Status")).isEqualTo("200"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
        }

        @Test
        @DeployApi("/apis/v4/api-request-console-log.json")
        void should_not_fail_when_using_console_log(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, getRequestedFor(urlPathEqualTo("/foobar")).withHeader("X-Logged", equalTo("true")));
        }

        @Test
        @DeployApi("/apis/v4/api-request-timeout.json")
        void should_return_500_on_infinite_loop_timeout(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-request-empty-script.json")
        void should_pass_through_with_empty_script(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, getRequestedFor(urlPathEqualTo("/foobar")));
        }

        @Test
        @DeployApi("/apis/v4/api-request-atob.json")
        void should_decode_base64_with_atob(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(1, getRequestedFor(urlPathEqualTo("/foobar")).withHeader("X-Decoded", equalTo("Hello World")));
        }

        @Test
        @DeployApi("/apis/v4/api-request-btoa-null.json")
        void should_return_500_on_btoa_null(HttpClient client) {
            wiremock.stubFor(get("/foobar").willReturn(ok("")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

            wiremock.verify(0, anyRequestedFor(anyUrl()));
        }

        @Test
        @DeployApi("/apis/v4/api-response-conditional.json")
        void should_handle_backend_error_response(HttpClient client) {
            wiremock.stubFor(get("/team").willReturn(aResponse().withStatus(502).withBody("Bad Gateway")));

            client
                .rxRequest(GET, "/test")
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(502))
                .doOnSuccess(response -> assertThat(response.getHeader("X-Error-Handled")).isEqualTo("true"))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> {
                    var json = new JsonObject(body.toString());
                    assertThat(json.getBoolean("error")).isTrue();
                    assertThat(json.getInteger("status")).isEqualTo(502);
                })
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();
        }
    }

    abstract static class AbstractMessageTest extends AbstractPolicyTest<JsPolicy, JsPolicyConfiguration> {

        @Override
        public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
            reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
        }
    }

    @GatewayTest
    @Nested
    class SubscribeTest extends AbstractMessageTest {

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-get", EntrypointBuilder.build("http-get", HttpGetEntrypointConnectorFactory.class));
        }

        @Test
        @DeployApi("/apis/v4/api-subscribe.json")
        void should_transform_subscribe_messages(HttpClient client) {
            client
                .rxRequest(GET, "/test")
                .map(request -> request.putHeader(HttpHeaderNames.ACCEPT.toString(), MediaType.APPLICATION_JSON))
                .flatMap(HttpClientRequest::rxSend)
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .flatMap(HttpClientResponse::body)
                .map(Buffer::toString)
                .map(json -> new JsonObject(json).getJsonArray("items"))
                .doOnSuccess(items -> assertThat(items).hasSize(2))
                .doOnSuccess(items ->
                    items.forEach(item -> {
                        var message = (JsonObject) item;
                        assertThat(message.getString("content")).isEqualTo(EXPECTED_CONTENT.toString());
                        var headers = message.getJsonObject("headers");
                        assertThat(headers.getJsonArray("x-phase").getString(0)).isEqualTo("on-response-message");
                    })
                )
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();
        }
    }

    @GatewayTest
    @Nested
    class PublishTest extends AbstractMessageTest {

        private MessageStorage messageStorage;

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("mock", EndpointBuilder.build("mock", PersistentMockEndpointConnectorFactory.class));
        }

        @BeforeEach
        void setUp() {
            messageStorage = getBean(MessageStorage.class);
        }

        @AfterEach
        void tearDown() {
            messageStorage.reset();
        }

        @Test
        @DeployApi("/apis/v4/api-publish.json")
        void should_transform_publish_messages(HttpClient client) {
            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(202))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            messageStorage
                .subject()
                .doOnNext(message -> assertThat(message.content()).hasToString(EXPECTED_CONTENT.toString()))
                .doOnNext(message -> assertThat(message.attributes()).containsEntry("message.mutated", true))
                .test()
                .assertNoErrors()
                .dispose();
        }

        @Test
        @DeployApi("/apis/v4/api-publish-script-error.json")
        void should_return_500_on_publish_script_error(HttpClient client) {
            client
                .rxRequest(POST, "/test")
                .flatMap(request -> request.rxSend(GIVEN_CONTENT.toString()))
                .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(500))
                .flatMap(HttpClientResponse::body)
                .doOnSuccess(body -> assertThat(body).hasToString("JavaScript execution failed"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertNoErrors()
                .assertComplete();

            messageStorage.subject().test().assertNoValues().dispose();
        }
    }
}
