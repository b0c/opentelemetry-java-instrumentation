/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.AgentServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class Jetty11Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-11.0";

  private static final boolean CAPTURE_EXPERIMENTAL_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER;

  static {
    AgentServletInstrumenterBuilder<HttpServletRequest, HttpServletResponse> builder =
        AgentServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
            .addContextCustomizer(
                (context, request, attributes) -> new AppServerBridge.Builder().init(context))
            .propagateOperationListenersToOnEnd();
    
    if (CAPTURE_EXPERIMENTAL_ATTRIBUTES) {
      builder.setHttpExperimentalAttributesGetter(new Jetty11ExperimentalHttpAttributesGetter());
    }
    
    INSTRUMENTER = builder.build(INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE);
  }

  private static final JettyHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new JettyHelper<>(INSTRUMENTER, Servlet5Accessor.INSTANCE);

  public static JettyHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  public static boolean shouldCaptureRequestBody() {
    return CAPTURE_EXPERIMENTAL_ATTRIBUTES;
  }

  private Jetty11Singletons() {}
}
