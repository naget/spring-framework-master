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
package org.springframework.web.reactive.accept;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;


/**
 * Factory to create a {@link CompositeContentTypeResolver} and configure it with
 * one or more {@link RequestedContentTypeResolver} instances with build style
 * methods. The following table shows methods, resulting strategy instances, and
 * if in use by default:
 *
 * <table>
 * <tr>
 *     <th>Property Setter</th>
 *     <th>Underlying Strategy</th>
 *     <th>Default Setting</th>
 * </tr>
 * <tr>
 *     <td>{@link #favorPathExtension}</td>
 *     <td>{@link PathExtensionContentTypeResolver Path Extension resolver}</td>
 *     <td>On</td>
 * </tr>
 * <tr>
 *     <td>{@link #favorParameter}</td>
 *     <td>{@link ParameterContentTypeResolver Parameter resolver}</td>
 *     <td>Off</td>
 * </tr>
 * <tr>
 *     <td>{@link #ignoreAcceptHeader}</td>
 *     <td>{@link HeaderContentTypeResolver Header resolver}</td>
 *     <td>Off</td>
 * </tr>
 * <tr>
 *     <td>{@link #defaultContentType}</td>
 *     <td>{@link FixedContentTypeResolver Fixed content resolver}</td>
 *     <td>Not set</td>
 * </tr>
 * <tr>
 *     <td>{@link #defaultContentTypeResolver}</td>
 *     <td>{@link RequestedContentTypeResolver}</td>
 *     <td>Not set</td>
 * </tr>
 * </table>
 *
 * <p>The order in which resolvers are configured is fixed. Config methods may
 * only turn individual resolvers on or off. If you need a custom order for any
 * reason simply instantiate {@code {@link CompositeContentTypeResolver}}
 * directly.
 *
 * <p>For the path extension and parameter resolvers you may explicitly add
 * {@link #mediaTypes(Map)}. This will be used to resolve path extensions or a
 * parameter value such as "json" to a media type such as "application/json".
 *
 * <p>The path extension strategy will also use
 * {@link org.springframework.http.MediaTypeFactory} to resolve a path extension
 * to a MediaType.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class RequestedContentTypeResolverBuilder {

	private boolean favorPathExtension = true;

	private boolean favorParameter = false;

	private boolean ignoreAcceptHeader = false;

	private Map<String, MediaType> mediaTypes = new HashMap<>();

	private boolean ignoreUnknownPathExtensions = true;

	private Boolean useRegisteredExtensionsOnly;

	private String parameterName = "format";

	private RequestedContentTypeResolver contentTypeResolver;


	/**
	 * Whether the path extension in the URL path should be used to determine
	 * the requested media type.
	 * <p>By default this is set to {@code true} in which case a request
	 * for {@code /hotels.pdf} will be interpreted as a request for
	 * {@code "application/pdf"} regardless of the 'Accept' header.
	 */
	public RequestedContentTypeResolverBuilder favorPathExtension(boolean favorPathExtension) {
		this.favorPathExtension = favorPathExtension;
		return this;
	}

	/**
	 * Add a mapping from a key, extracted from a path extension or a query
	 * parameter, to a MediaType. This is required in order for the parameter
	 * strategy to work. Any extensions explicitly registered here are also
	 * whitelisted for the purpose of Reflected File Download attack detection
	 * (see Spring Framework reference documentation for more details on RFD
	 * attack protection).
	 * <p>The path extension strategy will also use the
	 * {@link org.springframework.http.MediaTypeFactory} to resolve path
	 * extensions.
	 * @param mediaTypes media type mappings
	 */
	public RequestedContentTypeResolverBuilder mediaTypes(Map<String, MediaType> mediaTypes) {
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			for (Map.Entry<String, MediaType> entry : mediaTypes.entrySet()) {
				String extension = entry.getKey().toLowerCase(Locale.ENGLISH);
				this.mediaTypes.put(extension, entry.getValue());
			}
		}
		return this;
	}

	/**
	 * Alternative to {@link #mediaTypes} to add a single mapping.
	 */
	public RequestedContentTypeResolverBuilder mediaType(String key, MediaType mediaType) {
		this.mediaTypes.put(key, mediaType);
		return this;
	}

	/**
	 * Whether to ignore requests with path extension that cannot be resolved
	 * to any media type. Setting this to {@code false} will result in an
	 * {@link org.springframework.web.HttpMediaTypeNotAcceptableException} if
	 * there is no match.
	 * <p>By default this is set to {@code true}.
	 */
	public RequestedContentTypeResolverBuilder ignoreUnknownPathExtensions(boolean ignore) {
		this.ignoreUnknownPathExtensions = ignore;
		return this;
	}

	/**
	 * When {@link #favorPathExtension favorPathExtension} is set, this
	 * property determines whether to use only registered {@code MediaType} mappings
	 * to resolve a path extension to a specific MediaType.
	 * <p>By default this is not set in which case
	 * {@code PathExtensionContentNegotiationStrategy} will use defaults if available.
	 */
	public RequestedContentTypeResolverBuilder useRegisteredExtensionsOnly(boolean useRegisteredExtensionsOnly) {
		this.useRegisteredExtensionsOnly = useRegisteredExtensionsOnly;
		return this;
	}

	/**
	 * Whether a request parameter ("format" by default) should be used to
	 * determine the requested media type. For this option to work you must
	 * register {@link #mediaTypes media type mappings}.
	 * <p>By default this is set to {@code false}.
	 * @see #parameterName
	 */
	public RequestedContentTypeResolverBuilder favorParameter(boolean favorParameter) {
		this.favorParameter = favorParameter;
		return this;
	}

	/**
	 * Set the query parameter name to use when {@link #favorParameter} is on.
	 * <p>The default parameter name is {@code "format"}.
	 */
	public RequestedContentTypeResolverBuilder parameterName(String parameterName) {
		Assert.notNull(parameterName, "parameterName is required");
		this.parameterName = parameterName;
		return this;
	}

	/**
	 * Whether to disable checking the 'Accept' request header.
	 * <p>By default this value is set to {@code false}.
	 */
	public RequestedContentTypeResolverBuilder ignoreAcceptHeader(boolean ignoreAcceptHeader) {
		this.ignoreAcceptHeader = ignoreAcceptHeader;
		return this;
	}

	/**
	 * Set the default content type(s) to use when no content type is requested
	 * in order of priority.
	 *
	 * <p>If destinations are present that do not support any of the given media
	 * types, consider appending {@link MediaType#ALL} at the end.
	 *
	 * <p>By default this is not set.
	 *
	 * @see #defaultContentTypeResolver
	 */
	public RequestedContentTypeResolverBuilder defaultContentType(MediaType... contentTypes) {
		this.contentTypeResolver = new FixedContentTypeResolver(Arrays.asList(contentTypes));
		return this;
	}

	/**
	 * Set a custom {@link RequestedContentTypeResolver} to use to determine
	 * the content type to use when no content type is requested.
	 * <p>By default this is not set.
	 * @see #defaultContentType
	 */
	public RequestedContentTypeResolverBuilder defaultContentTypeResolver(RequestedContentTypeResolver resolver) {
		this.contentTypeResolver = resolver;
		return this;
	}


	public CompositeContentTypeResolver build() {
		List<RequestedContentTypeResolver> resolvers = new ArrayList<>();

		if (this.favorPathExtension) {
			PathExtensionContentTypeResolver resolver = new PathExtensionContentTypeResolver(this.mediaTypes);
			resolver.setIgnoreUnknownExtensions(this.ignoreUnknownPathExtensions);
			if (this.useRegisteredExtensionsOnly != null) {
				resolver.setUseRegisteredExtensionsOnly(this.useRegisteredExtensionsOnly);
			}
			resolvers.add(resolver);
		}

		if (this.favorParameter) {
			ParameterContentTypeResolver resolver = new ParameterContentTypeResolver(this.mediaTypes);
			resolver.setParameterName(this.parameterName);
			resolvers.add(resolver);
		}

		if (!this.ignoreAcceptHeader) {
			resolvers.add(new HeaderContentTypeResolver());
		}

		if (this.contentTypeResolver != null) {
			resolvers.add(this.contentTypeResolver);
		}

		return new CompositeContentTypeResolver(resolvers);
	}

}
