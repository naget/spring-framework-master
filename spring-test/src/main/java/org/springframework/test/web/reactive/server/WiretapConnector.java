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

package org.springframework.test.web.reactive.server;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.util.Assert;

/**
 * Decorate another {@link ClientHttpConnector} with the purpose of
 * intercepting, capturing, and exposing actual request and response data
 * transmitted to and received from the server.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see HttpHandlerConnector
 */
class WiretapConnector implements ClientHttpConnector {

	private final ClientHttpConnector delegate;

	private final Map<String, ExchangeResult> exchanges = new ConcurrentHashMap<>();


	WiretapConnector(ClientHttpConnector delegate) {
		this.delegate = delegate;
	}


	@Override
	public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
			Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		AtomicReference<WiretapClientHttpRequest> requestRef = new AtomicReference<>();

		return this.delegate
				.connect(method, uri, request -> {
					WiretapClientHttpRequest wrapped = new WiretapClientHttpRequest(request);
					requestRef.set(wrapped);
					return requestCallback.apply(wrapped);
				})
				.map(response ->  {
					WiretapClientHttpRequest wrappedRequest = requestRef.get();
					String requestId = wrappedRequest.getHeaders().getFirst(WebTestClient.WEBTESTCLIENT_REQUEST_ID);
					Assert.notNull(requestId, "No \"" + WebTestClient.WEBTESTCLIENT_REQUEST_ID + "\" header");
					WiretapClientHttpResponse wrappedResponse = new WiretapClientHttpResponse(response);
					ExchangeResult result = new ExchangeResult(wrappedRequest, wrappedResponse);
					this.exchanges.put(requestId, result);
					return wrappedResponse;
				});
	}

	/**
	 * Retrieve the {@code ExchangeResult} for the given "request-id" header value.
	 */
	public ExchangeResult claimRequest(String requestId) {
		ExchangeResult result = this.exchanges.get(requestId);
		Assert.notNull(result, "No match for " + WebTestClient.WEBTESTCLIENT_REQUEST_ID + "=" + requestId);
		return result;
	}

}
