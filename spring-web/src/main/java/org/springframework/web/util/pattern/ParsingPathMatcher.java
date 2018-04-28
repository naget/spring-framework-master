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

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.util.PathMatcher;

/**
 * {@link PathMatcher} implementation for path patterns parsed
 * as {@link PathPatternParser} and compiled as {@link PathPattern}s.
 *
 * <p>Once parsed, {@link PathPattern}s are tailored for fast matching
 * and quick comparison.
 *
 * <p>Calls this {@link PathMatcher} implementation can lead to
 * {@link PatternParseException} if the provided patterns are
 * illegal.
 *
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 5.0
 * @see PathPattern
 */
public class ParsingPathMatcher implements PathMatcher {

	private final PathPatternParser parser = new PathPatternParser();

	private final ConcurrentMap<String, PathPattern> cache = new ConcurrentHashMap<>(256);


	@Override
	public boolean isPattern(String path) {
		// TODO crude, should be smarter, lookup pattern and ask it
		return (path.indexOf('*') != -1 || path.indexOf('?') != -1);
	}

	@Override
	public boolean match(String pattern, String path) {
		PathPattern pathPattern = getPathPattern(pattern);
		return pathPattern.matches(path);
	}

	@Override
	public boolean matchStart(String pattern, String path) {
		PathPattern pathPattern = getPathPattern(pattern);
		return pathPattern.matchStart(path);
	}

	@Override
	public String extractPathWithinPattern(String pattern, String path) {
		PathPattern pathPattern = getPathPattern(pattern);
		return pathPattern.extractPathWithinPattern(path);
	}

	@Override
	public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
		PathPattern pathPattern = getPathPattern(pattern);
		return pathPattern.matchAndExtract(path);
	}

	@Override
	public Comparator<String> getPatternComparator(String path) {
		return new PathPatternStringComparatorConsideringPath(path);
	}

	@Override
	public String combine(String pattern1, String pattern2) {
		PathPattern pathPattern = getPathPattern(pattern1);
		return pathPattern.combine(pattern2);
	}

	private PathPattern getPathPattern(String pattern) {
		PathPattern pathPattern = this.cache.get(pattern);
		if (pathPattern == null) {
			pathPattern = this.parser.parse(pattern);
			this.cache.put(pattern, pathPattern);
		}
		return pathPattern;
	}


	private class PathPatternStringComparatorConsideringPath implements Comparator<String> {

		private final PatternComparatorConsideringPath ppcp;

		public PathPatternStringComparatorConsideringPath(String path) {
			this.ppcp = new PatternComparatorConsideringPath(path);
		}

		@Override
		public int compare(String o1, String o2) {
			if (o1 == null) {
				return (o2 == null ? 0 : +1);
			}
			else if (o2 == null) {
				return -1;
			}
			PathPattern p1 = getPathPattern(o1);
			PathPattern p2 = getPathPattern(o2);
			return this.ppcp.compare(p1, p2);
		}
	}


	/**
	 * {@link PathPattern} comparator that takes account of a specified
	 * path and sorts anything that exactly matches it to be first.
	 */
	static class PatternComparatorConsideringPath implements Comparator<PathPattern> {

		private final String path;

		public PatternComparatorConsideringPath(String path) {
			this.path = path;
		}

		@Override
		public int compare(PathPattern o1, PathPattern o2) {
			// Nulls get sorted to the end
			if (o1 == null) {
				return (o2 == null ? 0 : +1);
			}
			else if (o2 == null) {
				return -1;
			}
			if (o1.getPatternString().equals(this.path)) {
				return (o2.getPatternString().equals(this.path)) ? 0 : -1;
			}
			else if (o2.getPatternString().equals(this.path)) {
				return +1;
			}
			return o1.compareTo(o2);
		}
	}

}
