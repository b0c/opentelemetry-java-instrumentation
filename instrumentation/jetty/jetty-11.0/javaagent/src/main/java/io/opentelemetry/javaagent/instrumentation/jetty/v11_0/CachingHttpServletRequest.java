/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * HttpServletRequestWrapper that caches the request body for later access by the experimental
 * attributes extractor.
 */
public final class CachingHttpServletRequest extends HttpServletRequestWrapper {

  private static final int MAX_BODY_SIZE = 4096;

  @Nullable private byte[] cachedBody;
  @Nullable private CachingInputStream cachedInputStream;

  public CachingHttpServletRequest(HttpServletRequest request) {
    super(request);
  }

  @Nullable
  String getCachedBody() {
    // If body is fully cached, return it
    if (cachedBody != null) {
      return bodyAsString(cachedBody);
    }
    
    // If we have a caching input stream that's been reading, check its buffer
    if (cachedInputStream != null) {
      byte[] body = cachedInputStream.getCapturedBytes();
      if (body != null && body.length > 0) {
        return bodyAsString(body);
      }
    }
    
    return null;
  }
  
  private String bodyAsString(byte[] bodyBytes) {
    String charset = getCharacterEncoding();
    if (charset == null) {
      charset = StandardCharsets.UTF_8.name();
    }
    try {
      return new String(bodyBytes, charset);
    } catch (Exception e) {
      return new String(bodyBytes, StandardCharsets.UTF_8);
    }
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (cachedInputStream != null) {
      return cachedInputStream;
    }
    
    ServletInputStream original = super.getInputStream();
    
    if (!shouldCacheBody()) {
      // Content type is not cacheable, just return original stream
      return original;
    }
    
    // Wrap the original stream to capture bytes
    cachedInputStream = new CachingInputStream(original);
    return cachedInputStream;
  }

  @Override
  public java.io.BufferedReader getReader() throws IOException {
    String charset = getCharacterEncoding();
    if (charset == null) {
      charset = StandardCharsets.UTF_8.name();
    }
    return new java.io.BufferedReader(
        new java.io.InputStreamReader(getInputStream(), charset));
  }

  private boolean shouldCacheBody() {
    // Only cache if content type suggests it's textual
    String contentType = getContentType();
    if (contentType == null) {
      return false;
    }
    return contentType.contains("json")
        || contentType.contains("xml")
        || contentType.contains("text");
  }

  /**
   * ServletInputStream that captures bytes as they are read, allowing them to be cached for later
   * access
   */
  private class CachingInputStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    CachingInputStream(ServletInputStream delegate) {
      this.delegate = delegate;
    }

    @Nullable
    byte[] getCapturedBytes() {
      if (buffer.size() == 0) {
        return null;
      }
      return buffer.toByteArray();
    }

    @Override
    public int read() throws IOException {
      int byteValue = delegate.read();
      if (byteValue != -1) {
        if (buffer.size() < MAX_BODY_SIZE) {
          buffer.write(byteValue);
        }
      } else {
        // Stream ended, save what we have
        cachedBody = buffer.toByteArray();
      }
      return byteValue;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int bytesRead = delegate.read(b, off, len);
      if (bytesRead > 0 && buffer.size() < MAX_BODY_SIZE) {
        int toWrite = Math.min(bytesRead, MAX_BODY_SIZE - buffer.size());
        buffer.write(b, off, toWrite);
      }
      if (bytesRead == -1) {
        // Stream ended, save what we have
        cachedBody = buffer.toByteArray();
      }
      return bytesRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(jakarta.servlet.ReadListener readListener) {
      delegate.setReadListener(readListener);
    }
  }
}
