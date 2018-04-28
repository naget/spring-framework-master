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

package org.springframework.web.util.pattern;

/** 
 * Parser for URI template patterns. It breaks the path pattern into a number
 * of {@link PathElement}s in a linked list.
 * 
 * @author Andy Clement
 * @since 5.0
 */
public class PathPatternParser {

	public final static char DEFAULT_SEPARATOR = '/';


	// The expected path separator to split path elements during parsing.
	private char separator = DEFAULT_SEPARATOR;
	
	// Whether the PathPatterns produced by the parser will allow patterns that don't
	// have a trailing slash to match paths that may or may not have a trailing slash.
	private boolean matchOptionalTrailingSlash = false;

	// If the parser produces case-sensitive PathPattern matchers.
	private boolean caseSensitive = true;


	/**
	 * Create a path pattern parser that will use the default separator '/'
	 * when parsing patterns.
	 * @see #DEFAULT_SEPARATOR
	 */
	public PathPatternParser() {
	}

	/**
	 * Create a path pattern parser that will use the supplied separator when
	 * parsing patterns.
	 * @param separator the separator expected to divide pattern elements
	 */
	public PathPatternParser(char separator) {
		this.separator = separator;
	}


	/**
	 * Control behavior of the path patterns produced by this parser: if {@code true}
	 * then PathPatterns without a trailing slash will match paths with or without
	 * a trailing slash.
	 * <p>The default is {@code true} but here this flag can be set to {@code false}.
	 */
	public void setMatchOptionalTrailingSlash(boolean matchOptionalTrailingSlash) {
		this.matchOptionalTrailingSlash = matchOptionalTrailingSlash;
	}

	/**
	 * Set whether path patterns are case-sensitive.
	 * <p>The default is {@code true}.
	 */
	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}


	/**
	 * Process the path pattern data, a character at a time, breaking it into
	 * path elements around separator boundaries and verifying the structure at each
	 * stage. Produces a PathPattern object that can be used for fast matching
	 * against paths. Each invocation of this method delegates to a new instance of
	 * the {@link InternalPathPatternParser} because that class is not thread-safe.
	 * @param pathPattern the input path pattern, e.g. /foo/{bar}
	 * @return a PathPattern for quickly matching paths against the specified path pattern
	 * @throws PatternParseException in case of parse errors
	 */
	public PathPattern parse(String pathPattern) throws PatternParseException {
		InternalPathPatternParser parserDelegate =
				new InternalPathPatternParser(this.separator, this.caseSensitive, this.matchOptionalTrailingSlash);
		return parserDelegate.parse(pathPattern);
	}

}
