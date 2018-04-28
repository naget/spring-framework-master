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

package org.springframework.http.codec.json;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Encode from an {@code Object} stream to a byte stream of JSON objects,
 * using Jackson 2.9.
 *
 * @author Sebastien Deleuze
 * @author Arjen Poutsma
 * @since 5.0
 * @see Jackson2JsonDecoder
 */
public class Jackson2JsonEncoder extends Jackson2CodecSupport implements HttpMessageEncoder<Object> {

	private final List<MediaType> streamingMediaTypes = new ArrayList<>(1);

	private final PrettyPrinter ssePrettyPrinter;



	public Jackson2JsonEncoder() {
		this(Jackson2ObjectMapperBuilder.json().build());
	}

	public Jackson2JsonEncoder(ObjectMapper mapper, MimeType... mimeTypes) {
		super(mapper, mimeTypes);
		this.streamingMediaTypes.add(MediaType.APPLICATION_STREAM_JSON);
		this.ssePrettyPrinter = initSsePrettyPrinter();
	}

	private static PrettyPrinter initSsePrettyPrinter() {
		DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
		printer.indentObjectsWith(new DefaultIndenter("  ", "\ndata:"));
		return printer;
	}


	/**
	 * Configure "streaming" media types for which flushing should be performed
	 * automatically vs at the end of the stream.
	 * <p>By default this is set to {@link MediaType#APPLICATION_STREAM_JSON}.
	 * @param mediaTypes one or more media types to add to the list
	 * @see HttpMessageEncoder#getStreamingMediaTypes()
	 */
	public void setStreamingMediaTypes(List<MediaType> mediaTypes) {
		this.streamingMediaTypes.clear();
		this.streamingMediaTypes.addAll(mediaTypes);
	}

	@Override
	public List<MimeType> getEncodableMimeTypes() {
		return JSON_MIME_TYPES;
	}


	@Override
	public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
		Class<?> clazz = elementType.resolve(Object.class);
		return (Object.class == clazz) ||
				!String.class.isAssignableFrom(elementType.resolve(clazz)) &&
				this.objectMapper.canSerialize(clazz) && supportsMimeType(mimeType);
	}

	@Override
	public Flux<DataBuffer> encode(Publisher<?> inputStream, DataBufferFactory bufferFactory,
			ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		Assert.notNull(inputStream, "'inputStream' must not be null");
		Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
		Assert.notNull(elementType, "'elementType' must not be null");

		if (inputStream instanceof Mono) {
			return Flux.from(inputStream).map(value ->
					encodeValue(value, mimeType, bufferFactory, elementType, hints));
		}
		else if (MediaType.APPLICATION_STREAM_JSON.isCompatibleWith(mimeType)) {
			return Flux.from(inputStream).map(value -> {
				DataBuffer buffer = encodeValue(value, mimeType, bufferFactory, elementType, hints);
				buffer.write(new byte[]{'\n'});
				return buffer;
			});
		}
		else {
			ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, elementType);
			return Flux.from(inputStream).collectList().map(list ->
					encodeValue(list, mimeType, bufferFactory, listType, hints)).flux();
		}
	}

	private DataBuffer encodeValue(Object value, MimeType mimeType, DataBufferFactory bufferFactory,
			ResolvableType elementType, Map<String, Object> hints) {

		TypeFactory typeFactory = this.objectMapper.getTypeFactory();
		JavaType javaType = typeFactory.constructType(elementType.getType());
		if (elementType.isInstance(value)) {
			javaType = getJavaType(elementType.getType(), null);
		}

		Class<?> jsonView = (Class<?>) hints.get(Jackson2CodecSupport.JSON_VIEW_HINT);
		ObjectWriter writer = (jsonView != null ?
				this.objectMapper.writerWithView(jsonView) : this.objectMapper.writer());

		if (javaType != null && javaType.isContainerType()) {
			writer = writer.forType(javaType);
		}

		if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(mimeType) &&
				writer.getConfig().isEnabled(SerializationFeature.INDENT_OUTPUT)) {

			writer = writer.with(this.ssePrettyPrinter);
		}

		DataBuffer buffer = bufferFactory.allocateBuffer();
		OutputStream outputStream = buffer.asOutputStream();
		try {
			writer.writeValue(outputStream, value);
		}
		catch (InvalidDefinitionException ex) {
			throw new CodecException("Type definition error: " + ex.getType(), ex);
		}
		catch (JsonProcessingException ex) {
			throw new EncodingException("JSON encoding error: " + ex.getOriginalMessage(), ex);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unexpected I/O error while writing to data buffer", ex);
		}

		return buffer;
	}


	// HttpMessageEncoder...

	@Override
	public List<MediaType> getStreamingMediaTypes() {
		return Collections.unmodifiableList(this.streamingMediaTypes);
	}

	@Override
	public Map<String, Object> getEncodeHints(ResolvableType actualType, ResolvableType elementType,
			MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response) {

		return getHints(actualType);
	}

	@Override
	protected <A extends Annotation> A getAnnotation(MethodParameter parameter, Class<A> annotType) {
		return parameter.getMethodAnnotation(annotType);
	}

}
