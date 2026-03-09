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

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
}
