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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.Assert;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

/**
 * <strong>Central entry point to Spring's functional web framework.</strong>
 * Exposes routing functionality, such as to
 * {@linkplain #route(RequestPredicate, HandlerFunction) create} a {@code RouterFunction}
 * given a {@code RequestPredicate} and {@code HandlerFunction}, and to do further
 * {@linkplain #nest(RequestPredicate, RouterFunction) subrouting} on an existing routing
 * function.
 *
 * <p>Additionally, this class can {@linkplain #toHttpHandler(RouterFunction) transform} a
 * {@code RouterFunction} into an {@code HttpHandler}, which can be run in Servlet 3.1+,
 * Reactor, or Undertow.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class RouterFunctions {

	private static final Log logger = LogFactory.getLog(RouterFunctions.class);

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the {@link ServerRequest}.
	 */
	public static final String REQUEST_ATTRIBUTE = RouterFunctions.class.getName() + ".request";

	/**
	 * Name of the {@link ServerWebExchange} attribute that contains the URI
	 * templates map, mapping variable names to values.
	 */
	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE =
			RouterFunctions.class.getName() + ".uriTemplateVariables";

	private static final HandlerFunction<ServerResponse> NOT_FOUND_HANDLER = request -> ServerResponse.notFound().build();


	/**
	 * Route to the given handler function if the given request predicate applies.
	 * <p>For instance, the following example routes GET requests for "/user" to the
	 * {@code listUsers} method in {@code userController}:
	 * <pre class="code">
	 * RouterFunction&lt;ServerResponse&gt; route =
	 *     RouterFunctions.route(RequestPredicates.GET("/user"), userController::listUsers);
	 * </pre>
	 * @param predicate the predicate to test
	 * @param handlerFunction the handler function to route to if the predicate applies
	 * @param <T> the type of response returned by the handler function
	 * @return a router function that routes to {@code handlerFunction} if
	 * {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T extends ServerResponse> RouterFunction<T> route(RequestPredicate predicate,
			HandlerFunction<T> handlerFunction) {

		Assert.notNull(predicate, "'predicate' must not be null");
		Assert.notNull(handlerFunction, "'handlerFunction' must not be null");

		return new DefaultRouterFunction<>(predicate, handlerFunction);
	}

	/**
	 * Route to the given router function if the given request predicate applies. This method can be
	 * used to create <strong>nested routes</strong>, where a group of routes share a common path
	 * (prefix), header, or other request predicate.
	 * <p>For instance, the following example first creates a composed route that resolves to
	 * {@code listUsers} for a GET, and {@code createUser} for a POST. This composed route then gets
	 * nested with a "/user" path predicate, so that GET requests for "/user" will list users,
	 * and POST request for "/user" will create a new user.
	 * <pre class="code">
	 * RouterFunction&lt;ServerResponse&gt; userRoutes =
	 *   RouterFunctions.route(RequestPredicates.method(HttpMethod.GET), this::listUsers)
	 *     .andRoute(RequestPredicates.method(HttpMethod.POST), this::createUser);
	 *
	 * RouterFunction&lt;ServerResponse&gt; nestedRoute =
	 *   RouterFunctions.nest(RequestPredicates.path("/user"),userRoutes);
	 * </pre>
	 * @param predicate the predicate to test
	 * @param routerFunction the nested router function to delegate to if the predicate applies
	 * @param <T> the type of response returned by the handler function
	 * @return a router function that routes to {@code routerFunction} if
	 * {@code predicate} evaluates to {@code true}
	 * @see RequestPredicates
	 */
	public static <T extends ServerResponse> RouterFunction<T> nest(RequestPredicate predicate,
			RouterFunction<T> routerFunction) {

		Assert.notNull(predicate, "'predicate' must not be null");
		Assert.notNull(routerFunction, "'routerFunction' must not be null");

		return new DefaultNestedRouterFunction<>(predicate, routerFunction);
	}

	/**
	 * Route requests that match the given pattern to resources relative to the given root location.
	 * For instance
	 * <pre class="code">
	 * Resource location = new FileSystemResource("public-resources/");
	 * RoutingFunction&lt;ServerResponse&gt; resources = RouterFunctions.resources("/resources/**", location);
     * </pre>
	 * @param pattern the pattern to match
	 * @param location the location directory relative to which resources should be resolved
	 * @return a router function that routes to resources
	 */
	public static RouterFunction<ServerResponse> resources(String pattern, Resource location) {
		Assert.hasLength(pattern, "'pattern' must not be empty");
		Assert.notNull(location, "'location' must not be null");
		return resources(new PathResourceLookupFunction(pattern, location));
	}

	/**
	 * Route to resources using the provided lookup function. If the lookup function provides a
	 * {@link Resource} for the given request, it will be it will be exposed using a
	 * {@link HandlerFunction} that handles GET, HEAD, and OPTIONS requests.
	 * @param lookupFunction the function to provide a {@link Resource} given the {@link ServerRequest}
	 * @return a router function that routes to resources
	 */
	public static RouterFunction<ServerResponse> resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {
		Assert.notNull(lookupFunction, "'lookupFunction' must not be null");
		return request -> lookupFunction.apply(request).map(ResourceHandlerFunction::new);
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link HttpHandler}.
	 * This conversion uses {@linkplain HandlerStrategies#builder() default strategies}.
	 * <p>The returned handler can be adapted to run in
	 * <ul>
	 * <li>Servlet 3.1+ using the
	 * {@link org.springframework.http.server.reactive.ServletHttpHandlerAdapter},</li>
	 * <li>Reactor using the
	 * {@link org.springframework.http.server.reactive.ReactorHttpHandlerAdapter},</li>
	 * <li>Undertow using the
	 * {@link org.springframework.http.server.reactive.UndertowHttpHandlerAdapter}.</li>
	 * </ul>
	 * <p>Note that {@code HttpWebHandlerAdapter} also implements {@link WebHandler}, allowing
	 * for additional filter and exception handler registration through
	 * {@link WebHttpHandlerBuilder}.
	 * @param routerFunction the router function to convert
	 * @return an http handler that handles HTTP request using the given router function
	 */
	public static HttpHandler toHttpHandler(RouterFunction<?> routerFunction) {
		return toHttpHandler(routerFunction, HandlerStrategies.withDefaults());
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link HttpHandler},
	 * using the given strategies.
	 * <p>The returned {@code HttpHandler} can be adapted to run in
	 * <ul>
	 * <li>Servlet 3.1+ using the
	 * {@link org.springframework.http.server.reactive.ServletHttpHandlerAdapter},</li>
	 * <li>Reactor using the
	 * {@link org.springframework.http.server.reactive.ReactorHttpHandlerAdapter},</li>
	 * <li>Undertow using the
	 * {@link org.springframework.http.server.reactive.UndertowHttpHandlerAdapter}.</li>
	 * </ul>
	 * @param routerFunction the router function to convert
	 * @param strategies the strategies to use
	 * @return an http handler that handles HTTP request using the given router function
	 */
	public static HttpHandler toHttpHandler(RouterFunction<?> routerFunction, HandlerStrategies strategies) {
		Assert.notNull(routerFunction, "RouterFunction must not be null");
		Assert.notNull(strategies, "HandlerStrategies must not be null");

		WebHandler webHandler = toWebHandler(routerFunction, strategies);
		return WebHttpHandlerBuilder.webHandler(webHandler)
				.filters(strategies.webFilters())
				.exceptionHandlers(strategies.exceptionHandlers())
				.build();
	}

	/**
	 * Convert the given {@linkplain RouterFunction router function} into a {@link WebHandler},
	 * using the given strategies.
	 * @param routerFunction the router function to convert
	 * @param strategies the strategies to use
	 * @return a web handler that handles web request using the given router function
	 */
	public static WebHandler toWebHandler(RouterFunction<?> routerFunction, HandlerStrategies strategies) {
		Assert.notNull(routerFunction, "RouterFunction must not be null");
		Assert.notNull(strategies, "HandlerStrategies must not be null");

		return exchange -> {
			ServerRequest request = new DefaultServerRequest(exchange, strategies.messageReaders());
			addAttributes(exchange, request);
			return routerFunction.route(request)
					.defaultIfEmpty(notFound())
					.flatMap(handlerFunction -> wrapException(() -> handlerFunction.handle(request)))
					.flatMap(response -> wrapException(() -> response.writeTo(exchange,
							new HandlerStrategiesResponseContext(strategies))));
		};
	}

	private static <T> Mono<T> wrapException(Supplier<Mono<T>> supplier) {
		try {
			return supplier.get();
		}
		catch (Throwable t) {
			return Mono.error(t);
		}
	}

	private static void addAttributes(ServerWebExchange exchange, ServerRequest request) {
		Map<String, Object> attributes = exchange.getAttributes();
		attributes.put(REQUEST_ATTRIBUTE, request);
	}

	@SuppressWarnings("unchecked")
	private static <T extends ServerResponse> HandlerFunction<T> notFound() {
		return (HandlerFunction<T>) NOT_FOUND_HANDLER;
	}

	@SuppressWarnings("unchecked")
	static <T extends ServerResponse> HandlerFunction<T> cast(HandlerFunction<?> handlerFunction) {
		return (HandlerFunction<T>) handlerFunction;
	}

	private static class DefaultRouterFunction<T extends ServerResponse>
			implements RouterFunction<T> {

		private final RequestPredicate predicate;

		private final HandlerFunction<T> handlerFunction;

		public DefaultRouterFunction(RequestPredicate predicate,
				HandlerFunction<T> handlerFunction) {
			this.predicate = predicate;
			this.handlerFunction = handlerFunction;
		}

		@Override
		public Mono<HandlerFunction<T>> route(ServerRequest request) {
			if (this.predicate.test(request)) {
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("Predicate \"%s\" matches against \"%s\"",
							this.predicate, request));
				}
				return Mono.just(this.handlerFunction);
			}
			else {
				return Mono.empty();
			}
		}

		@Override
		public String toString() {
			return String.format("%s -> %s", this.predicate, this.handlerFunction);
		}
	}

	private static class DefaultNestedRouterFunction<T extends ServerResponse>
			implements RouterFunction<T> {

		private final RequestPredicate predicate;

		private final RouterFunction<T> routerFunction;

		public DefaultNestedRouterFunction(RequestPredicate predicate,
				RouterFunction<T> routerFunction) {
			this.predicate = predicate;
			this.routerFunction = routerFunction;
		}

		@Override
		public Mono<HandlerFunction<T>> route(ServerRequest serverRequest) {
			return this.predicate.nest(serverRequest)
					.map(nestedRequest -> {
								if (logger.isDebugEnabled()) {
									logger.debug(
											String.format(
													"Nested predicate \"%s\" matches against \"%s\"",
													this.predicate, serverRequest));
								}
								return this.routerFunction.route(nestedRequest);
							}
					)
					.orElseGet(Mono::empty);
		}

		@Override
		public String toString() {
			return String.format("%s -> %s", this.predicate, this.routerFunction);
		}

	}

	private static class HandlerStrategiesResponseContext implements ServerResponse.Context {

		private final HandlerStrategies strategies;

		public HandlerStrategiesResponseContext(HandlerStrategies strategies) {
			this.strategies = strategies;
		}

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return this.strategies.messageWriters();
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return this.strategies.viewResolvers();
		}
	}
}
