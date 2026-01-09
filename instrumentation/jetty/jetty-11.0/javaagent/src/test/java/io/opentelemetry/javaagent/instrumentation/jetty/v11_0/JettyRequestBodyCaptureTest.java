/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES_WITHOUT_ROUTE;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyRequestBodyCaptureTest extends AbstractHttpServerTest<Server> {

  private static final AttributeKey<String> HTTP_REQUEST_BODY =
      AttributeKey.stringKey("http.request.body");

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private static final TestHandler testHandler = new TestHandler();

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);
    server.setHandler(testHandler);
    server.start();
    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(unused -> DEFAULT_HTTP_ATTRIBUTES_WITHOUT_ROUTE);
    // Disable unrelated default tests from AbstractHttpServerTest; this class focuses solely on
    // request body capture behavior.
    options.setTestRedirect(false);
    options.setTestError(false);
    options.setTestException(false);
    options.setTestCaptureHttpHeaders(false);
    options.setTestHttpPipelining(false);
  }

  @Test
  void captureRequestBody() throws Exception {
    String requestBody = "{\"test\":\"value\",\"foo\":\"bar\"}";

    java.net.URI uri = address.resolve(SUCCESS.getPath());
    java.net.HttpURLConnection connection =
        (java.net.HttpURLConnection) uri.toURL().openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setDoOutput(true);
    connection.getOutputStream().write(requestBody.getBytes(Charset.defaultCharset()));
    int responseCode = connection.getResponseCode();
    assertThat(responseCode).isEqualTo(SUCCESS.getStatus());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            equalTo(
                                HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE,
                                (long) requestBody.length()),
                            equalTo(HTTP_REQUEST_BODY, requestBody)),
                span -> span.hasName("controller").hasKind(SpanKind.INTERNAL)));
  }

  private static void handleRequest(
      Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(baseRequest.getRequestURI());
    controller(
        endpoint,
        () -> {
          try {
            return response(baseRequest, request, response, endpoint);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static HttpServletResponse response(
      Request baseRequest,
      HttpServletRequest request,
      HttpServletResponse response,
      ServerEndpoint endpoint)
      throws IOException {
    response.setContentType("text/plain");
    if (SUCCESS.equals(endpoint)) {
      // For body capture test, consume request body to trigger caching
      if ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod())) {
        try {
          BufferedReader reader = request.getReader();
          while (reader.readLine() != null) {
            // consume
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      response.setStatus(endpoint.getStatus());
      response.getWriter().print(endpoint.getBody());
    } else if (ServerEndpoint.QUERY_PARAM.equals(endpoint)) {
      response.setStatus(endpoint.getStatus());
      response.getWriter().print(baseRequest.getQueryString());
    } else if (ServerEndpoint.REDIRECT.equals(endpoint)) {
      response.sendRedirect(endpoint.getBody());
    } else if (ServerEndpoint.ERROR.equals(endpoint)) {
      response.sendError(endpoint.getStatus(), endpoint.getBody());
    } else if (ServerEndpoint.CAPTURE_HEADERS.equals(endpoint)) {
      response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"));
      response.setStatus(endpoint.getStatus());
      response.getWriter().print(endpoint.getBody());
    } else if (ServerEndpoint.EXCEPTION.equals(endpoint)) {
      throw new IllegalStateException(endpoint.getBody());
    } else if (ServerEndpoint.INDEXED_CHILD.equals(endpoint)) {
      ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(baseRequest::getParameter);
      response.setStatus(endpoint.getStatus());
      response.getWriter().print(endpoint.getBody());
    } else {
      response.setStatus(404);
      response.getWriter().print("Not Found");
    }
    return response;
  }

  private static class TestHandler extends AbstractHandler {
    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {
      handleRequest(baseRequest, request, response);
      baseRequest.setHandled(true);
    }
  }
}
