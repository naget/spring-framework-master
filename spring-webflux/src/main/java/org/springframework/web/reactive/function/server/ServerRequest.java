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

package org.springframework.web.reactive.function.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.json.Jackson2CodecSupport;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

/**
 * Represents a server-side HTTP request, as handled by a {@code HandlerFunction}.
 * Access to headers and body is offered by {@link Headers} and
 * {@link #body(BodyExtractor)}, respectively.
 *
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 * @since 5.0
 */
public interface ServerRequest {

	/**
	 * Return the HTTP method.
	 */
	HttpMethod method();

	/**
	 * Return the request URI.
	 */
	URI uri();

	/**
	 * Return the request path.
	 */
	default String path() {
		return uri().getPath();
	}

	/**
	 * Return the headers of this request.
	 */
	Headers headers();

	/**
	 * Extract the body with the given {@code BodyExtractor}.
	 * @param extractor the {@code BodyExtractor} that reads from the request
	 * @param <T> the type of the body returned
	 * @return the extracted body
	 * @see #body(BodyExtractor, Map)
	 */
	<T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor);

	/**
	 * Extract the body with the given {@code BodyExtractor} and hints.
	 * @param extractor the {@code BodyExtractor} that reads from the request
	 * @param hints the map of hints like {@link Jackson2CodecSupport#JSON_VIEW_HINT}
	 * to use to customize body extraction
	 * @param <T> the type of the body returned
	 * @return the extracted body
	 */
	<T> T body(BodyExtractor<T, ? super ServerHttpRequest> extractor, Map<String, Object> hints);

	/**
	 * Extract the body to a {@code Mono}.
	 * @param elementClass the class of element in the {@code Mono}
	 * @param <T> the element type
	 * @return the body as a mono
	 */
	<T> Mono<T> bodyToMono(Class<? extends T> elementClass);

	/**
	 * Extract the body to a {@code Flux}.
	 * @param elementClass the class of element in the {@code Flux}
	 * @param <T> the element type
	 * @return the body as a flux
	 */
	<T> Flux<T> bodyToFlux(Class<? extends T> elementClass);

	/**
	 * Return the request attribute value if present.
	 * @param name the attribute name
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	<T> Optional<T> attribute(String name);

	/**
	 * Return a mutable map of request attributes.
	 * @return the request attributes
	 */
	Map<String, Object> attributes();

	/**
	 * Return the first query parameter with the given name, if present.
	 * @param name the parameter name
	 * @return the parameter value
	 */
	default Optional<String> queryParam(String name) {
		List<String> queryParams = this.queryParams(name);
		if (queryParams.isEmpty()) {
			return Optional.empty();
		}
		else {
			String value = queryParams.get(0);
			if (value == null) {
				value = "";
			}
			return Optional.of(value);
		}
	}

	/**
	 * Return all query parameter with the given name.
	 * <p>Returns an empty list if no values could be found.
	 * @param name the parameter name
	 * @return the parameter values
	 */
	List<String> queryParams(String name);

	/**
	 * Return the path variable with the given name, if present.
	 * @param name the variable name
	 * @return the variable value
	 * @throws IllegalArgumentException if there is no path variable with the given name
	 */
	default String pathVariable(String name) {
		Map<String, String> pathVariables = pathVariables();
		if (pathVariables.containsKey(name)) {
			return pathVariables().get(name);
		}
		else {
			throw new IllegalArgumentException("No path variable with name \"" + name + "\" available");
		}
	}

	/**
	 * Return all path variables for the current request.
	 * @return a {@code Map} from path variable name to associated value
	 */
	Map<String, String> pathVariables();

	/**
	 * Return the web session for the current request. Always guaranteed  to
	 * return an instance either matching to the session id requested by the
	 * client, or with a new session id either because the client did not
	 * specify one or because the underlying session had expired. Use of this
	 * method does not automatically create a session.
	 */
	Mono<WebSession> session();

	/**
	 * Return the authenticated user for the request, if any.
	 */
	Mono<? extends Principal> principal();


	/**
	 * Create a new {@code ServerRequest} based on the given {@code ServerWebExchange} and
	 * message readers.
	 * @param exchange the exchange
	 * @param messageReaders the message readers
	 * @return the created {@code ServerRequest}
	 */
	static ServerRequest create(ServerWebExchange exchange,
			List<HttpMessageReader<?>> messageReaders) {

		return new DefaultServerRequest(exchange, messageReaders);
	}

	/**
	 * Represents the headers of the HTTP request.
	 * @see ServerRequest#headers()
	 */
	interface Headers {

		/**
		 * Return the list of acceptable {@linkplain MediaType media types},
		 * as specified by the {@code Accept} header.
		 * <p>Returns an empty list when the acceptable media types are unspecified.
		 */
		List<MediaType> accept();

		/**
		 * Return the list of acceptable {@linkplain Charset charsets},
		 * as specified by the {@code Accept-Charset} header.
		 */
		List<Charset> acceptCharset();

		/**
		 * Return the list of acceptable {@linkplain Locale.LanguageRange languages},
		 * as specified by the {@code Accept-Language} header.
		 */
		List<Locale.LanguageRange> acceptLanguage();

		/**
		 * Return the length of the body in bytes, as specified by the
		 * {@code Content-Length} header.
		 */
		OptionalLong contentLength();

		/**
		 * Return the {@linkplain MediaType media type} of the body, as specified
		 * by the {@code Content-Type} header.
		 */
		Optional<MediaType> contentType();

		/**
		 * Return the value of the required {@code Host} header.
		 * <p>If the header value does not contain a port, the returned
		 * {@linkplain InetSocketAddress#getPort() port} will be {@code 0}.
		 */
		InetSocketAddress host();

		/**
		 * Return the value of the {@code Range} header.
		 * <p>Returns an empty list when the range is unknown.
		 */
		List<HttpRange> range();

		/**
		 * Return the header value(s), if any, for the header of the given name.
		 * <p>Return an empty list if no header values are found.
		 * @param headerName the header name
		 */
		List<String> header(String headerName);

		/**
		 * Return the headers as a {@link HttpHeaders} instance.
		 */
		HttpHeaders asHttpHeaders();
	}

}
