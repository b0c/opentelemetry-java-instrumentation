/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.servlet.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import javax.annotation.Nullable;

final class Jetty11ExperimentalHttpAttributesGetter
    implements HttpServerAttributesGetter<
            ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>,
        HttpExperimentalAttributesExtractor.HttpRequestBodyGetter<
            ServletRequestContext<HttpServletRequest>> {

  private final ServletHttpAttributesGetter<HttpServletRequest, HttpServletResponse> delegate =
      new ServletHttpAttributesGetter<>(Servlet5Accessor.INSTANCE);

  @Override
  public String getHttpRequestMethod(ServletRequestContext<HttpServletRequest> request) {
    return delegate.getHttpRequestMethod(request);
  }

  @Override
  public List<String> getHttpRequestHeader(
      ServletRequestContext<HttpServletRequest> request, String name) {
    return delegate.getHttpRequestHeader(request, name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ServletRequestContext<HttpServletRequest> request,
      ServletResponseContext<HttpServletResponse> response,
      @Nullable Throwable error) {
    return delegate.getHttpResponseStatusCode(request, response, error);
  }

  @Override
  public List<String> getHttpResponseHeader(
      ServletRequestContext<HttpServletRequest> request,
      ServletResponseContext<HttpServletResponse> response,
      String name) {
    return delegate.getHttpResponseHeader(request, response, name);
  }

  @Override
  @Nullable
  public String getUrlScheme(ServletRequestContext<HttpServletRequest> request) {
    return delegate.getUrlScheme(request);
  }

  @Override
  @Nullable
  public String getUrlPath(ServletRequestContext<HttpServletRequest> request) {
    return delegate.getUrlPath(request);
  }

  @Override
  @Nullable
  public String getUrlQuery(ServletRequestContext<HttpServletRequest> request) {
    return delegate.getUrlQuery(request);
  }

  @Override
  @Nullable
  public String getHttpRequestBody(ServletRequestContext<HttpServletRequest> request) {
    HttpServletRequest servletRequest = request.request();
    if (servletRequest instanceof CachingHttpServletRequest) {
      return ((CachingHttpServletRequest) servletRequest).getCachedBody();
    }
    return null;
  }
}
