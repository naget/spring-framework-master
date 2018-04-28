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

package org.springframework.web.reactive.result.method.annotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.InvocableHandlerMethod;
import org.springframework.web.server.ServerWebExchange;

/**
 * Package-private class to assist {@link RequestMappingHandlerAdapter} with
 * default model initialization through {@code @ModelAttribute} methods.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
class ModelInitializer {

	private final ReactiveAdapterRegistry adapterRegistry;


	public ModelInitializer(ReactiveAdapterRegistry adapterRegistry) {
		this.adapterRegistry = adapterRegistry;
	}


	/**
	 * Initialize the default model in the given {@code BindingContext} through
	 * the {@code @ModelAttribute} methods and indicate when complete.
	 * <p>This will wait for {@code @ModelAttribute} methods that return
	 * {@code Mono<Void>} since those may be adding attributes asynchronously.
	 * However if methods return async attributes, those will be added to the
	 * model as-is and without waiting for them to be resolved.
	 * @param bindingContext the BindingContext with the default model
	 * @param attributeMethods the {@code @ModelAttribute} methods
	 * @param exchange the current exchange
	 * @return a {@code Mono} for when the model is populated.
	 */
	@SuppressWarnings("Convert2MethodRef")
	public Mono<Void> initModel(BindingContext bindingContext,
			List<InvocableHandlerMethod> attributeMethods, ServerWebExchange exchange) {

		List<Mono<HandlerResult>> resultList = new ArrayList<>();
		attributeMethods.forEach(invocable -> resultList.add(invocable.invoke(exchange, bindingContext)));

		return Mono.when(resultList, objectArray -> {
			return Arrays.stream(objectArray)
					.map(object -> (HandlerResult) object)
					.map(handlerResult -> handleResult(handlerResult, bindingContext))
					.collect(Collectors.toList());
		}).flatMap(completionList -> Mono.when(completionList));
	}

	private Mono<Void> handleResult(HandlerResult handlerResult, BindingContext bindingContext) {
		Object value = handlerResult.getReturnValue();
		if (value != null) {
			ResolvableType type = handlerResult.getReturnType();
			ReactiveAdapter adapter = this.adapterRegistry.getAdapter(type.getRawClass(), value);
			if (adapter != null) {
				Class<?> attributeType = (adapter.isNoValue() ? Void.class : type.resolveGeneric());
				if (attributeType == Void.class) {
					return Mono.from(adapter.toPublisher(value));
				}
			}
			String name = getAttributeName(handlerResult.getReturnTypeSource());
			bindingContext.getModel().asMap().putIfAbsent(name, value);
		}
		return Mono.empty();
	}

	private String getAttributeName(MethodParameter param) {
		return Optional
				.ofNullable(AnnotatedElementUtils.findMergedAnnotation(param.getMethod(), ModelAttribute.class))
				.filter(ann -> StringUtils.hasText(ann.value()))
				.map(ModelAttribute::value)
				.orElse(Conventions.getVariableNameForParameter(param));
	}

}
