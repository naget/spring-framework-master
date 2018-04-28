/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.socket.server.upgrade;

import java.security.Principal;
import java.util.Optional;

import io.reactivex.netty.protocol.http.HttpHandlerNames;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.ws.server.WebSocketHandshaker;
import reactor.core.publisher.Mono;
import rx.RxReactiveStreams;

import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.server.reactive.RxNettyServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.RxNettyWebSocketSession;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@link RequestUpgradeStrategy} for use with RxNetty.
 * For internal use within the framework.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class RxNettyRequestUpgradeStrategy implements RequestUpgradeStrategy {

	@Override
	public Mono<Void> upgrade(ServerWebExchange exchange, WebSocketHandler handler, @Nullable String subProtocol) {
		RxNettyServerHttpResponse response = (RxNettyServerHttpResponse) exchange.getResponse();
		HttpServerResponse<?> rxNettyResponse = response.getRxNettyResponse();

		HandshakeInfo info = getHandshakeInfo(exchange, subProtocol);
		NettyDataBufferFactory factory = (NettyDataBufferFactory) response.bufferFactory();

		WebSocketHandshaker handshaker = rxNettyResponse
				.acceptWebSocketUpgrade(conn -> {
					RxNettyWebSocketSession session = new RxNettyWebSocketSession(conn, info, factory);
					String name = HttpHandlerNames.WsServerDecoder.getName();
					session.aggregateFrames(rxNettyResponse.unsafeNettyChannel(), name);
					return RxReactiveStreams.toObservable(handler.handle(session));
				});

		if (subProtocol != null) {
			handshaker = handshaker.subprotocol(subProtocol);
		}
		else {
			// TODO: https://github.com/reactor/reactor-netty/issues/20
			handshaker = handshaker.subprotocol();
		}

		return Mono.from(RxReactiveStreams.toPublisher(handshaker));
	}

	private HandshakeInfo getHandshakeInfo(ServerWebExchange exchange, String protocol) {
		ServerHttpRequest request = exchange.getRequest();
		Mono<Principal> principal = exchange.getPrincipal();
		return new HandshakeInfo(request.getURI(), request.getHeaders(), principal, protocol);
	}

}
