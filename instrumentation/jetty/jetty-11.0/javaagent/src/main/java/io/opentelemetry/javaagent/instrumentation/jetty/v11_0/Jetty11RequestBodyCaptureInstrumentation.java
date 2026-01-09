/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments Jetty's Request to wrap getReader() result and capture up to a limited number of
 * characters for experimental http.request.body attribute when enabled.
 */
public class Jetty11RequestBodyCaptureInstrumentation implements TypeInstrumentation {

  static final String ATTR_REQUEST_BODY =
      Jetty11RequestBodyCaptureInstrumentation.class.getName() + ".capturedBody";

  private static final boolean ENABLED =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);

  private static final int MAX_BODY_CHARS = 4096;

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return net.bytebuddy.matcher.ElementMatchers.named("org.eclipse.jetty.server.Request");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Wrap getReader; handler in tests uses Request.getReader(), not HttpServletRequest
    transformer.applyAdviceToMethod(
        named("getReader"),
        Jetty11RequestBodyCaptureInstrumentation.class.getName() + "$GetReaderAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetReaderAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This jakarta.servlet.http.HttpServletRequest request,
        @Advice.Return(readOnly = false) BufferedReader reader) {
      if (!ENABLED) {
        return;
      }
      String contentType = request.getContentType();
      if (contentType == null
          || !(contentType.contains("json")
              || contentType.contains("xml")
              || contentType.contains("text"))) {
        return;
      }

      // Reuse existing builder if present to support multiple reads
      @Nullable Object existing = request.getAttribute(ATTR_REQUEST_BODY);
      StringBuilder builder = existing instanceof StringBuilder ? (StringBuilder) existing : null;
      if (builder == null) {
        builder = new StringBuilder(Math.min(256, MAX_BODY_CHARS));
        request.setAttribute(ATTR_REQUEST_BODY, builder);
      }

      // Wrap returned reader so subsequent reads capture characters
      reader = new BufferedReader(new CapturingReader(reader, builder));
    }
  }

  /** Reader wrapper that captures characters into provided builder up to a limit. */
  static class CapturingReader extends Reader {
    private final Reader delegate;
    private final StringBuilder sink;

    CapturingReader(Reader delegate, StringBuilder sink) {
      this.delegate = delegate;
      this.sink = sink;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      int read = delegate.read(cbuf, off, len);
      if (read > 0) {
        int remaining = MAX_BODY_CHARS - sink.length();
        if (remaining > 0) {
          int toAppend = Math.min(read, remaining);
          sink.append(cbuf, off, toAppend);
        }
      }
      return read;
    }

    @Override
    public int read() throws IOException {
      int ch = delegate.read();
      if (ch != -1 && sink.length() < MAX_BODY_CHARS) {
        sink.append((char) ch);
      }
      return ch;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
