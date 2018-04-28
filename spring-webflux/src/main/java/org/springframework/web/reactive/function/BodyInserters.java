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

package org.springframework.web.reactive.function;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * Implementations of {@link BodyInserter} that write various bodies, such a reactive streams,
 * server-sent events, resources, etc.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class BodyInserters {

	private static final ResolvableType RESOURCE_TYPE =
			ResolvableType.forClass(Resource.class);

	private static final ResolvableType SERVER_SIDE_EVENT_TYPE =
			ResolvableType.forClass(ServerSentEvent.class);

	private static final ResolvableType FORM_TYPE =
			ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class);

	private static final ResolvableType MULTIPART_VALUE_TYPE = ResolvableType.forClassWithGenerics(
			MultiValueMap.class, String.class, Part.class);

	private static final BodyInserter<Void, ReactiveHttpOutputMessage> EMPTY =
					(response, context) -> response.setComplete();


	/**
	 * Return an empty {@code BodyInserter} that writes nothing.
	 * @return an empty {@code BodyInserter}
	 */
	@SuppressWarnings("unchecked")
	public static <T> BodyInserter<T, ReactiveHttpOutputMessage> empty() {
		return (BodyInserter<T, ReactiveHttpOutputMessage>)EMPTY;
	}

	/**
	 * Return a {@code BodyInserter} that writes the given single object.
	 * @param body the body of the response
	 * @return a {@code BodyInserter} that writes a single object
	 */
	public static <T> BodyInserter<T, ReactiveHttpOutputMessage> fromObject(T body) {
		Assert.notNull(body, "'body' must not be null");
		return bodyInserterFor(Mono.just(body), ResolvableType.forInstance(body));
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@link Publisher}.
	 * @param publisher the publisher to stream to the response body
	 * @param elementClass the class of elements contained in the publisher
	 * @param <T> the type of the elements contained in the publisher
	 * @param <P> the type of the {@code Publisher}
	 * @return a {@code BodyInserter} that writes a {@code Publisher}
	 */
	public static <T, P extends Publisher<T>> BodyInserter<P, ReactiveHttpOutputMessage> fromPublisher(
			P publisher, Class<T> elementClass) {

		Assert.notNull(publisher, "'publisher' must not be null");
		Assert.notNull(elementClass, "'elementClass' must not be null");
		return bodyInserterFor(publisher, ResolvableType.forClass(elementClass));
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@link Publisher}.
	 * @param publisher the publisher to stream to the response body
	 * @param elementType the type of elements contained in the publisher
	 * @param <T> the type of the elements contained in the publisher
	 * @param <P> the type of the {@code Publisher}
	 * @return a {@code BodyInserter} that writes a {@code Publisher}
	 */
	public static <T, P extends Publisher<T>> BodyInserter<P, ReactiveHttpOutputMessage> fromPublisher(
			P publisher, ResolvableType elementType) {

		Assert.notNull(publisher, "'publisher' must not be null");
		Assert.notNull(elementType, "'elementType' must not be null");
		return bodyInserterFor(publisher, elementType);
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code Resource}.
	 * <p>If the resource can be resolved to a {@linkplain Resource#getFile() file}, it will
	 * be copied using <a href="https://en.wikipedia.org/wiki/Zero-copy">zero-copy</a>.
	 * @param resource the resource to write to the output message
	 * @param <T> the type of the {@code Resource}
	 * @return a {@code BodyInserter} that writes a {@code Publisher}
	 */
	public static <T extends Resource> BodyInserter<T, ReactiveHttpOutputMessage> fromResource(T resource) {
		Assert.notNull(resource, "'resource' must not be null");
		return (outputMessage, context) -> {
			Mono<T> inputStream = Mono.just(resource);
			HttpMessageWriter<Resource> messageWriter = resourceHttpMessageWriter(context);
			Optional<ServerHttpRequest> serverRequest = context.serverRequest();
			if (serverRequest.isPresent() && outputMessage instanceof ServerHttpResponse) {
				return messageWriter.write(inputStream, RESOURCE_TYPE, RESOURCE_TYPE, null,
						serverRequest.get(), (ServerHttpResponse) outputMessage, context.hints());
			}
			else {
				return messageWriter.write(inputStream, RESOURCE_TYPE, null,
						outputMessage, context.hints());
			}
		};
	}

	private static HttpMessageWriter<Resource> resourceHttpMessageWriter(BodyInserter.Context context) {
		return context.messageWriters().stream()
				.filter(messageWriter -> messageWriter.canWrite(RESOURCE_TYPE, null))
				.findFirst()
				.map(BodyInserters::<Resource>cast)
				.orElseThrow(() -> new IllegalStateException(
						"Could not find HttpMessageWriter that supports Resource objects"));
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code ServerSentEvent} publisher.
	 * @param eventsPublisher the {@code ServerSentEvent} publisher to write to the response body
	 * @param <T> the type of the elements contained in the {@link ServerSentEvent}
	 * @return a {@code BodyInserter} that writes a {@code ServerSentEvent} publisher
	 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommendation</a>
	 */
	// Note that the returned BodyInserter is parameterized to ServerHttpResponse, not
	// ReactiveHttpOutputMessage like other methods, since sending SSEs only typically happens on
	// the server-side
	public static <T, S extends Publisher<ServerSentEvent<T>>> BodyInserter<S, ServerHttpResponse> fromServerSentEvents(
			S eventsPublisher) {

		Assert.notNull(eventsPublisher, "'eventsPublisher' must not be null");
		return (serverResponse, context) -> {
			HttpMessageWriter<ServerSentEvent<T>> messageWriter =
					findMessageWriter(context, SERVER_SIDE_EVENT_TYPE, MediaType.TEXT_EVENT_STREAM);
			return context.serverRequest()
					.map(serverRequest -> messageWriter.write(eventsPublisher, SERVER_SIDE_EVENT_TYPE,
							SERVER_SIDE_EVENT_TYPE, MediaType.TEXT_EVENT_STREAM, serverRequest,
							serverResponse, context.hints()))
					.orElseGet(() -> messageWriter.write(eventsPublisher, SERVER_SIDE_EVENT_TYPE,
							MediaType.TEXT_EVENT_STREAM, serverResponse, context.hints()));
		};
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code Publisher} publisher as
	 * Server-Sent Events.
	 * @param eventsPublisher the publisher to write to the response body as Server-Sent Events
	 * @param eventClass the class of event contained in the publisher
	 * @param <T> the type of the elements contained in the publisher
	 * @return a {@code BodyInserter} that writes the given {@code Publisher} publisher as
	 * Server-Sent Events
	 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommendation</a>
	 */
	// Note that the returned BodyInserter is parameterized to ServerHttpResponse, not
	// ReactiveHttpOutputMessage like other methods, since sending SSEs only typically happens on
	// the server-side
	public static <T, S extends Publisher<T>> BodyInserter<S, ServerHttpResponse> fromServerSentEvents(S eventsPublisher,
			Class<T> eventClass) {

		Assert.notNull(eventsPublisher, "'eventsPublisher' must not be null");
		Assert.notNull(eventClass, "'eventClass' must not be null");
		return fromServerSentEvents(eventsPublisher, ResolvableType.forClass(eventClass));
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code Publisher} publisher as
	 * Server-Sent Events.
	 * @param eventsPublisher the publisher to write to the response body as Server-Sent Events
	 * @param eventType the type of event contained in the publisher
	 * @param <T> the type of the elements contained in the publisher
	 * @return a {@code BodyInserter} that writes the given {@code Publisher} publisher as
	 * Server-Sent Events
	 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommendation</a>
	 */
	// Note that the returned BodyInserter is parameterized to ServerHttpResponse, not
	// ReactiveHttpOutputMessage like other methods, since sending SSEs only typically happens on
	// the server-side
	public static <T, S extends Publisher<T>> BodyInserter<S, ServerHttpResponse> fromServerSentEvents(S eventsPublisher,
			ResolvableType eventType) {

		Assert.notNull(eventsPublisher, "'eventsPublisher' must not be null");
		Assert.notNull(eventType, "'eventType' must not be null");
		return (serverResponse, context) -> {
			HttpMessageWriter<T> messageWriter =
					findMessageWriter(context, SERVER_SIDE_EVENT_TYPE, MediaType.TEXT_EVENT_STREAM);
			return context.serverRequest()
					.map(serverRequest -> messageWriter.write(eventsPublisher, eventType,
							eventType, MediaType.TEXT_EVENT_STREAM, serverRequest,
							serverResponse, context.hints()))
					.orElseGet(() -> messageWriter.write(eventsPublisher, eventType,
							MediaType.TEXT_EVENT_STREAM, serverResponse, context.hints()));
		};
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code MultiValueMap} as URL-encoded
	 * form data.
	 * @param formData the form data to write to the output message
	 * @return a {@code BodyInserter} that writes form data
	 */
	// Note that the returned BodyInserter is parameterized to ClientHttpRequest, not
	// ReactiveHttpOutputMessage like other methods, since sending form data only typically happens
	// on the server-side
	public static BodyInserter<MultiValueMap<String, String>, ClientHttpRequest> fromFormData(
			MultiValueMap<String, String> formData) {

		Assert.notNull(formData, "'formData' must not be null");
		return (outputMessage, context) -> {
			HttpMessageWriter<MultiValueMap<String, String>> messageWriter =
					findMessageWriter(context, FORM_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
			return messageWriter.write(Mono.just(formData), FORM_TYPE,
					MediaType.APPLICATION_FORM_URLENCODED, outputMessage, context.hints());
		};
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code MultiValueMap} as Multipart
	 * data.
	 * @param multipartData the form data to write to the output message
	 * @return a {@code BodyInserter} that writes form data
	 */
	// Note that the returned BodyInserter is parameterized to ClientHttpRequest, not
	// ReactiveHttpOutputMessage like other methods, since sending form data only typically happens
	// on the server-side
	public static BodyInserter<MultiValueMap<String, ?>, ClientHttpRequest> fromMultipartData(
			MultiValueMap<String, ?> multipartData) {

		Assert.notNull(multipartData, "'multipartData' must not be null");
		return (outputMessage, context) -> {
			HttpMessageWriter<MultiValueMap<String, ?>> messageWriter =
					findMessageWriter(context, MULTIPART_VALUE_TYPE, MediaType.MULTIPART_FORM_DATA);
			return messageWriter.write(Mono.just(multipartData), FORM_TYPE,
					MediaType.MULTIPART_FORM_DATA, outputMessage, context.hints());
		};
	}

	/**
	 * Return a {@code BodyInserter} that writes the given {@code Publisher<DataBuffer>} to the body.
	 * @param publisher the data buffer publisher to write
	 * @param <T> the type of the publisher
	 * @return a {@code BodyInserter} that writes directly to the body
	 * @see ReactiveHttpOutputMessage#writeWith(Publisher)
	 */
	public static <T extends Publisher<DataBuffer>> BodyInserter<T, ReactiveHttpOutputMessage> fromDataBuffers(
			T publisher) {

		Assert.notNull(publisher, "'publisher' must not be null");
		return (outputMessage, context) -> outputMessage.writeWith(publisher);
	}


	private static <T, P extends Publisher<?>, M extends ReactiveHttpOutputMessage> BodyInserter<T, M> bodyInserterFor(
			P body, ResolvableType bodyType) {

		return (outputMessage, context) -> {
			MediaType contentType = outputMessage.getHeaders().getContentType();
			List<HttpMessageWriter<?>> messageWriters = context.messageWriters();
			return messageWriters.stream()
					.filter(messageWriter -> messageWriter.canWrite(bodyType, contentType))
					.findFirst()
					.map(BodyInserters::cast)
					.map(messageWriter -> {
						Optional<ServerHttpRequest> serverRequest = context.serverRequest();
						if (serverRequest.isPresent() && outputMessage instanceof ServerHttpResponse) {
							return messageWriter.write(body, bodyType, bodyType, contentType,
									serverRequest.get(), (ServerHttpResponse) outputMessage,
									context.hints());
						} else {
							return messageWriter.write(body, bodyType, contentType, outputMessage,
											context.hints());
						}
					})
					.orElseGet(() -> {
						List<MediaType> supportedMediaTypes = messageWriters.stream()
								.flatMap(reader -> reader.getWritableMediaTypes().stream())
								.collect(Collectors.toList());
						UnsupportedMediaTypeException error =
								new UnsupportedMediaTypeException(contentType, supportedMediaTypes);
						return Mono.error(error);
					});
		};
	}

	private static <T> HttpMessageWriter<T> findMessageWriter(
			BodyInserter.Context context, ResolvableType type, MediaType mediaType) {

		return context.messageWriters().stream()
				.filter(messageWriter -> messageWriter.canWrite(type, mediaType))
				.findFirst()
				.map(BodyInserters::<T>cast)
				.orElseThrow(() -> new IllegalStateException(
						"Could not find HttpMessageWriter that supports " + mediaType));
	}

	@SuppressWarnings("unchecked")
	private static <T> HttpMessageWriter<T> cast(HttpMessageWriter<?> messageWriter) {
		return (HttpMessageWriter<T>) messageWriter;
	}

}
