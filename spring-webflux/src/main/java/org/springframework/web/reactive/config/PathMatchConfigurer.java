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

package org.springframework.web.reactive.config;

import org.springframework.util.PathMatcher;
import org.springframework.web.server.support.HttpRequestPathHelper;
import org.springframework.web.util.pattern.ParsingPathMatcher;

/**
 * Assist with configuring {@code HandlerMapping}'s with path matching options.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class PathMatchConfigurer {

	private Boolean suffixPatternMatch;

	private Boolean trailingSlashMatch;

	private Boolean registeredSuffixPatternMatch;

	private HttpRequestPathHelper pathHelper;

	private PathMatcher pathMatcher;


	/**
	 * Whether to use suffix pattern match (".*") when matching patterns to
	 * requests. If enabled a method mapped to "/users" also matches to "/users.*".
	 * <p>By default this is set to {@code true}.
	 * @see #registeredSuffixPatternMatch
	 */
	public PathMatchConfigurer setUseSuffixPatternMatch(Boolean suffixPatternMatch) {
		this.suffixPatternMatch = suffixPatternMatch;
		return this;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 * If enabled a method mapped to "/users" also matches to "/users/".
	 * <p>The default value is {@code true}.
	 */
	public PathMatchConfigurer setUseTrailingSlashMatch(Boolean trailingSlashMatch) {
		this.trailingSlashMatch = trailingSlashMatch;
		return this;
	}

	/**
	 * Whether suffix pattern matching should work only against path extensions
	 * that are explicitly registered. This is generally recommended to reduce
	 * ambiguity and to avoid issues such as when a "." (dot) appears in the path
	 * for other reasons.
	 * <p>By default this is set to "true".
	 */
	public PathMatchConfigurer setUseRegisteredSuffixPatternMatch(Boolean registeredSuffixPatternMatch) {
		this.registeredSuffixPatternMatch = registeredSuffixPatternMatch;
		return this;
	}

	/**
	 * Set a {@code HttpRequestPathHelper} for the resolution of lookup paths.
	 * <p>Default is {@code HttpRequestPathHelper}.
	 */
	public PathMatchConfigurer setPathHelper(HttpRequestPathHelper pathHelper) {
		this.pathHelper = pathHelper;
		return this;
	}

	/**
	 * Set the PathMatcher for matching URL paths against registered URL patterns.
	 * <p>The default is a {@link org.springframework.web.util.pattern.ParsingPathMatcher}.
	 */
	public PathMatchConfigurer setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
		return this;
	}

	protected Boolean isUseSuffixPatternMatch() {
		return this.suffixPatternMatch;
	}

	protected Boolean isUseTrailingSlashMatch() {
		return this.trailingSlashMatch;
	}

	protected Boolean isUseRegisteredSuffixPatternMatch() {
		return this.registeredSuffixPatternMatch;
	}

	protected HttpRequestPathHelper getPathHelper() {
		return this.pathHelper;
	}

	public PathMatcher getPathMatcher() {
		if(this.pathMatcher != null
				&& this.pathMatcher.getClass().isAssignableFrom(ParsingPathMatcher.class)
				&& (this.trailingSlashMatch || this.suffixPatternMatch)) {
			throw new IllegalStateException("When using a ParsingPathMatcher, useTrailingSlashMatch" +
					" and useSuffixPatternMatch should be set to 'false'.");
		}
		return this.pathMatcher;
	}

}
