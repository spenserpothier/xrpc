/*
 * Copyright 2018 Nordstrom, Inc.
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

package com.nordstrom.xrpc.server;

import com.codahale.metrics.Meter;
import com.nordstrom.xrpc.XrpcConstants;
import com.nordstrom.xrpc.client.XUrl;
import com.nordstrom.xrpc.server.http.Recipes;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class UrlRouter extends ChannelDuplexHandler {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    XrpcConnectionContext xctx = ctx.channel().attr(XrpcConstants.CONNECTION_CONTEXT).get();
    xctx.requestMeter().mark();

    if (ctx.channel().hasAttr(XrpcConstants.XRPC_SOFT_RATE_LIMITED)) {
      ctx.writeAndFlush(
              Recipes.newResponse(
                  HttpResponseStatus.TOO_MANY_REQUESTS,
                  Unpooled.wrappedBuffer(XrpcConstants.RATE_LIMIT_RESPONSE),
                  Recipes.ContentType.Text_Plain))
          .addListener(ChannelFutureListener.CLOSE);
      xctx.metersByStatusCode().get(HttpResponseStatus.TOO_MANY_REQUESTS).mark();
      return;
    }

    if (msg instanceof FullHttpRequest) {
      FullHttpRequest request = (FullHttpRequest) msg;
      String path = XUrl.path(request.uri());
      CompiledRoutes.Match match = xctx.routes().match(path, request.method());

      XrpcRequest xrpcRequest = new XrpcRequest(request, xctx, match.getGroups(), ctx.channel());

      HttpResponse resp = match.getHandler().handle(xrpcRequest);

      // TODO(jkinkead): Per issue #152, this should track ALL response codes.
      Meter meter = xctx.metersByStatusCode().get(resp.status());
      if (meter != null) {
        meter.mark();
      }

      ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
    ctx.fireChannelRead(msg);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelReadComplete();
  }
}
