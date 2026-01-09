/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.internal.HttpClientUrlTemplateUtil;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

public final class HttpExperimentalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from HttpIncubatingAttributes
  static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE =
      AttributeKey.longKey("http.request.body.size");
  static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE =
      AttributeKey.longKey("http.response.body.size");
  static final AttributeKey<String> HTTP_REQUEST_BODY =
      AttributeKey.stringKey("http.request.body");

  private static final int MAX_REQUEST_BODY_LENGTH = 4096;

  // copied from UrlIncubatingAttributes
  private static final AttributeKey<String> URL_TEMPLATE = AttributeKey.stringKey("url.template");

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new HttpExperimentalAttributesExtractor<>(getter);
  }

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new HttpExperimentalAttributesExtractor<>(getter);
  }

  private final HttpCommonAttributesGetter<REQUEST, RESPONSE> getter;

  private HttpExperimentalAttributesExtractor(
      HttpCommonAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    if (getter instanceof HttpClientAttributesGetter) {
      HttpClientAttributesGetter<REQUEST, RESPONSE> clientGetter =
          (HttpClientAttributesGetter<REQUEST, RESPONSE>) getter;
      String urlTemplate =
          HttpClientUrlTemplateUtil.getUrlTemplate(parentContext, request, clientGetter);
      internalSet(attributes, URL_TEMPLATE, urlTemplate);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    String requestBody = requestBody(request);
    internalSet(attributes, HTTP_REQUEST_BODY, requestBody);

    Long requestBodySize = requestBodySize(request);
    internalSet(attributes, HTTP_REQUEST_BODY_SIZE, requestBodySize);

    if (response != null) {
      Long responseBodySize = responseBodySize(request, response);
      internalSet(attributes, HTTP_RESPONSE_BODY_SIZE, responseBodySize);
    }
  }

  @Nullable
  private Long requestBodySize(REQUEST request) {
    return parseNumber(firstHeaderValue(getter.getHttpRequestHeader(request, "content-length")));
  }

  @Nullable
  private Long responseBodySize(REQUEST request, RESPONSE response) {
    return parseNumber(
        firstHeaderValue(getter.getHttpResponseHeader(request, response, "content-length")));
  }

  @Nullable
  static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  @Nullable
  private static Long parseNumber(@Nullable String number) {
    if (number == null) {
      return null;
    }
    try {
      return Long.parseLong(number);
    } catch (NumberFormatException e) {
      // not a number
      return null;
    }
  }

  @Nullable
  private String requestBody(REQUEST request) {
    if (!(getter instanceof HttpRequestBodyGetter)) {
      return null;
    }
    @SuppressWarnings("unchecked")
    // safe cast due to check above
    HttpRequestBodyGetter<REQUEST> bodyGetter = (HttpRequestBodyGetter<REQUEST>) getter;
    String body = bodyGetter.getHttpRequestBody(request);
    if (body == null) {
      return null;
    }
    if (body.length() > MAX_REQUEST_BODY_LENGTH) {
      return body.substring(0, MAX_REQUEST_BODY_LENGTH) + "... [truncated]";
    }
    return body;
  }

  public interface HttpRequestBodyGetter<REQUEST> {
    @Nullable
    String getHttpRequestBody(REQUEST request);
  }
}
