package com.nordstrom.xrpc.server;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

import com.nordstrom.xrpc.encoding.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.util.Map;

public interface ResponseFactory {
  XrpcRequest request();

  default HttpResponse ok() {
    return createResponse(
        HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER, HttpHeaderValues.TEXT_PLAIN);
  }

  default <T> HttpResponse ok(T body) throws IOException {
    return createResponse(HttpResponseStatus.OK, body);
  }

  default <T> HttpResponse badRequest(T body) throws IOException {
    return createResponse(HttpResponseStatus.BAD_REQUEST, body);
  }

  default <T> HttpResponse notFound(T body) throws IOException {
    return createResponse(HttpResponseStatus.NOT_FOUND, body);
  }

  default <T> HttpResponse forbidden(T body) throws IOException {
    return createResponse(HttpResponseStatus.FORBIDDEN, body);
  }

  default <T> HttpResponse unauthorized(T body) throws IOException {
    return createResponse(HttpResponseStatus.UNAUTHORIZED, body);
  }

  default <T> HttpResponse internalServerError(T body) throws IOException {
    return createResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, body);
  }

  default <T> HttpResponse createResponse(HttpResponseStatus status, T body) throws IOException {
    final Encoder encoder =
        request().connectionContext().encoders().acceptedEncoder(request().acceptHeader());
    ByteBuf buf = request().byteBuf();
    return createResponse(
        status, encoder.encode(buf, request().acceptCharsetHeader(), body), encoder.mediaType());
  }

  default HttpResponse createResponse(
      HttpResponseStatus status, ByteBuf body, CharSequence contentType) {
    return createResponse(status, body, contentType, null);
  }

  default HttpResponse createResponse(
      HttpResponseStatus status,
      ByteBuf body,
      CharSequence contentType,
      Map<String, String> customHeaders) {

    HttpResponse response =
        body == null || body.readableBytes() <= 0
            ? new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
            : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body);

    if (customHeaders != null) {
      customHeaders.forEach((key, value) -> response.headers().set(key, value));
    }

    response.headers().set(CONTENT_TYPE, contentType);
    response.headers().setInt(CONTENT_LENGTH, body == null ? 0 : body.readableBytes());

    return response;
  }
}
