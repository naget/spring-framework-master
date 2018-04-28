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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.accept.CompositeContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter;
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping;
import org.springframework.web.reactive.function.server.support.ServerResponseResultHandler;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.reactive.result.SimpleHandlerAdapter;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityResultHandler;
import org.springframework.web.reactive.result.view.ViewResolutionResultHandler;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.handler.ResponseStatusExceptionHandler;

/**
 * The main class for Spring WebFlux configuration.
 *
 * <p>Import directly or extend and override protected methods to customize.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class WebFluxConfigurationSupport implements ApplicationContextAware {

	static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper",
					WebFluxConfigurationSupport.class.getClassLoader()) &&
			ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator",
					WebFluxConfigurationSupport.class.getClassLoader());


	private Map<String, CorsConfiguration> corsConfigurations;

	private PathMatchConfigurer pathMatchConfigurer;

	private ViewResolverRegistry viewResolverRegistry;

	private ApplicationContext applicationContext;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	protected ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	@Bean
	public DispatcherHandler webHandler() {
		return new DispatcherHandler();
	}

	@Bean
	@Order(0)
	public WebExceptionHandler responseStatusExceptionHandler() {
		return new ResponseStatusExceptionHandler();
	}

	@Bean
	public RequestMappingHandlerMapping requestMappingHandlerMapping() {
		RequestMappingHandlerMapping mapping = createRequestMappingHandlerMapping();
		mapping.setOrder(0);
		mapping.setContentTypeResolver(webFluxContentTypeResolver());
		mapping.setCorsConfigurations(getCorsConfigurations());

		PathMatchConfigurer configurer = getPathMatchConfigurer();
		if (configurer.isUseSuffixPatternMatch() != null) {
			mapping.setUseSuffixPatternMatch(configurer.isUseSuffixPatternMatch());
		}
		if (configurer.isUseRegisteredSuffixPatternMatch() != null) {
			mapping.setUseRegisteredSuffixPatternMatch(configurer.isUseRegisteredSuffixPatternMatch());
		}
		if (configurer.isUseTrailingSlashMatch() != null) {
			mapping.setUseTrailingSlashMatch(configurer.isUseTrailingSlashMatch());
		}
		if (configurer.getPathMatcher() != null) {
			mapping.setPathMatcher(configurer.getPathMatcher());
		}
		if (configurer.getPathHelper() != null) {
			mapping.setPathHelper(configurer.getPathHelper());
		}

		return mapping;
	}

	/**
	 * Override to plug a sub-class of {@link RequestMappingHandlerMapping}.
	 */
	protected RequestMappingHandlerMapping createRequestMappingHandlerMapping() {
		return new RequestMappingHandlerMapping();
	}

	@Bean
	public CompositeContentTypeResolver webFluxContentTypeResolver() {
		RequestedContentTypeResolverBuilder builder = new RequestedContentTypeResolverBuilder();
		builder.mediaTypes(getDefaultMediaTypeMappings());
		configureContentTypeResolver(builder);
		return builder.build();
	}

	/**
	 * Override to configure media type mappings.
	 * @see RequestedContentTypeResolverBuilder#mediaTypes(Map)
	 */
	protected Map<String, MediaType> getDefaultMediaTypeMappings() {
		Map<String, MediaType> map = new HashMap<>();
		if (jackson2Present) {
			map.put("json", MediaType.APPLICATION_JSON);
		}
		return map;
	}

	/**
	 * Override to configure how the requested content type is resolved.
	 */
	protected void configureContentTypeResolver(RequestedContentTypeResolverBuilder builder) {
	}

	/**
	 * Callback for building the global CORS configuration. This method is final.
	 * Use {@link #addCorsMappings(CorsRegistry)} to customize the CORS conifg.
	 */
	protected final Map<String, CorsConfiguration> getCorsConfigurations() {
		if (this.corsConfigurations == null) {
			CorsRegistry registry = new CorsRegistry();
			addCorsMappings(registry);
			this.corsConfigurations = registry.getCorsConfigurations();
		}
		return this.corsConfigurations;
	}

	/**
	 * Override this method to configure cross origin requests processing.
	 * @see CorsRegistry
	 */
	protected void addCorsMappings(CorsRegistry registry) {
	}

	/**
	 * Callback for building the {@link PathMatchConfigurer}. This method is
	 * final, use {@link #configurePathMatching} to customize path matching.
	 */
	protected final PathMatchConfigurer getPathMatchConfigurer() {
		if (this.pathMatchConfigurer == null) {
			this.pathMatchConfigurer = new PathMatchConfigurer();
			configurePathMatching(this.pathMatchConfigurer);
		}
		return this.pathMatchConfigurer;
	}

	/**
	 * Override to configure path matching options.
	 */
	public void configurePathMatching(PathMatchConfigurer configurer) {
	}

	@Bean
	public RouterFunctionMapping routerFunctionMapping() {
		RouterFunctionMapping mapping = createRouterFunctionMapping();
		mapping.setOrder(-1); // go before RequestMappingHandlerMapping
		mapping.setMessageCodecConfigurer(serverCodecConfigurer());
		mapping.setCorsConfigurations(getCorsConfigurations());

		return mapping;
	}

	/**
	 * Override to plug a sub-class of {@link RouterFunctionMapping}.
	 */
	protected RouterFunctionMapping createRouterFunctionMapping() {
		return new RouterFunctionMapping();
	}

	/**
	 * Return a handler mapping ordered at Integer.MAX_VALUE-1 with mapped
	 * resource handlers. To configure resource handling, override
	 * {@link #addResourceHandlers}.
	 */
	@Bean
	public HandlerMapping resourceHandlerMapping() {
		ResourceHandlerRegistry registry =
				new ResourceHandlerRegistry(this.applicationContext, webFluxContentTypeResolver());
		addResourceHandlers(registry);

		AbstractHandlerMapping handlerMapping = registry.getHandlerMapping();
		if (handlerMapping != null) {
			PathMatchConfigurer pathMatchConfigurer = getPathMatchConfigurer();
			if (pathMatchConfigurer.getPathMatcher() != null) {
				handlerMapping.setPathMatcher(pathMatchConfigurer.getPathMatcher());
			}
			if (pathMatchConfigurer.getPathHelper() != null) {
				handlerMapping.setPathHelper(pathMatchConfigurer.getPathHelper());
			}
		}
		else {
			handlerMapping = new EmptyHandlerMapping();
		}
		return handlerMapping;
	}

	/**
	 * Override this method to add resource handlers for serving static resources.
	 * @see ResourceHandlerRegistry
	 */
	protected void addResourceHandlers(ResourceHandlerRegistry registry) {
	}

	@Bean
	public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
		RequestMappingHandlerAdapter adapter = createRequestMappingHandlerAdapter();
		adapter.setMessageCodecConfigurer(serverCodecConfigurer());
		adapter.setWebBindingInitializer(getConfigurableWebBindingInitializer());
		adapter.setReactiveAdapterRegistry(webFluxAdapterRegistry());

		ArgumentResolverConfigurer configurer = new ArgumentResolverConfigurer();
		configureArgumentResolvers(configurer);
		adapter.setArgumentResolverConfigurer(configurer);

		return adapter;
	}

	/**
	 * Override to plug a sub-class of {@link RequestMappingHandlerAdapter}.
	 */
	protected RequestMappingHandlerAdapter createRequestMappingHandlerAdapter() {
		return new RequestMappingHandlerAdapter();
	}

	/**
	 * Configure resolvers for custom controller method arguments.
	 */
	protected void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
	}

	/**
	 * Return the configurer for HTTP message readers and writers.
	 * <p>Use {@link #configureHttpMessageCodecs(ServerCodecConfigurer)} to
	 * configure the readers and writers.
	 */
	@Bean
	public ServerCodecConfigurer serverCodecConfigurer() {
		ServerCodecConfigurer serverCodecConfigurer = ServerCodecConfigurer.create();
		configureHttpMessageCodecs(serverCodecConfigurer);
		return serverCodecConfigurer;
	}

	/**
	 * Override to configure the HTTP message readers and writers to use.
	 */
	protected void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
	}

	/**
	 * Return the {@link ConfigurableWebBindingInitializer} to use for
	 * initializing all {@link WebDataBinder} instances.
	 */
	protected ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer() {
		ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
		initializer.setConversionService(webFluxConversionService());
		initializer.setValidator(webFluxValidator());
		initializer.setMessageCodesResolver(getMessageCodesResolver());
		return initializer;
	}

	@Bean
	public FormattingConversionService webFluxConversionService() {
		FormattingConversionService service = new DefaultFormattingConversionService();
		addFormatters(service);
		return service;
	}

	/**
	 * Override to add custom {@link Converter}s and {@link Formatter}s.
	 */
	protected void addFormatters(FormatterRegistry registry) {
	}

	/**
	 * Return a {@link ReactiveAdapterRegistry} to adapting reactive types.
	 */
	@Bean
	public ReactiveAdapterRegistry webFluxAdapterRegistry() {
		return new ReactiveAdapterRegistry();
	}

	/**
	 * Return a global {@link Validator} instance for example for validating
	 * {@code @RequestBody} method arguments.
	 * <p>Delegates to {@link #getValidator()} first. If that returns {@code null}
	 * checks the classpath for the presence of a JSR-303 implementations
	 * before creating a {@code OptionalValidatorFactoryBean}. If a JSR-303
	 * implementation is not available, a "no-op" {@link Validator} is returned.
	 */
	@Bean
	public Validator webFluxValidator() {
		Validator validator = getValidator();
		if (validator == null) {
			if (ClassUtils.isPresent("javax.validation.Validator", getClass().getClassLoader())) {
				Class<?> clazz;
				try {
					String name = "org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean";
					clazz = ClassUtils.forName(name, getClass().getClassLoader());
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException("Could not find default validator class", ex);
				}
				catch (LinkageError ex) {
					throw new BeanInitializationException("Could not load default validator class", ex);
				}
				validator = (Validator) BeanUtils.instantiateClass(clazz);
			}
			else {
				validator = new NoOpValidator();
			}
		}
		return validator;
	}

	/**
	 * Override this method to provide a custom {@link Validator}.
	 */
	@Nullable
	protected Validator getValidator() {
		return null;
	}

	/**
	 * Override this method to provide a custom {@link MessageCodesResolver}.
	 */
	@Nullable
	protected MessageCodesResolver getMessageCodesResolver() {
		return null;
	}

	@Bean
	public HandlerFunctionAdapter handlerFunctionAdapter() {
		return new HandlerFunctionAdapter();
	}

	@Bean
	public SimpleHandlerAdapter simpleHandlerAdapter() {
		return new SimpleHandlerAdapter();
	}

	@Bean
	public ResponseEntityResultHandler responseEntityResultHandler() {
		return new ResponseEntityResultHandler(serverCodecConfigurer().getWriters(),
				webFluxContentTypeResolver(), webFluxAdapterRegistry());
	}

	@Bean
	public ResponseBodyResultHandler responseBodyResultHandler() {
		return new ResponseBodyResultHandler(serverCodecConfigurer().getWriters(),
				webFluxContentTypeResolver(), webFluxAdapterRegistry());
	}

	@Bean
	public ViewResolutionResultHandler viewResolutionResultHandler() {
		ViewResolverRegistry registry = getViewResolverRegistry();
		List<ViewResolver> resolvers = registry.getViewResolvers();
		ViewResolutionResultHandler handler = new ViewResolutionResultHandler(
				resolvers, webFluxContentTypeResolver(), webFluxAdapterRegistry());
		handler.setDefaultViews(registry.getDefaultViews());
		handler.setOrder(registry.getOrder());
		return handler;
	}

	@Bean
	public ServerResponseResultHandler serverResponseResultHandler() {
		ViewResolverRegistry registry = getViewResolverRegistry();
		List<ViewResolver> resolvers = registry.getViewResolvers();

		ServerResponseResultHandler handler = new ServerResponseResultHandler();
		handler.setMessageCodecConfigurer(serverCodecConfigurer());
		handler.setViewResolvers(resolvers);
		handler.setOrder(registry.getOrder() + 1);

		return handler;
	}

	/**
	 * Callback for building the {@link ViewResolverRegistry}. This method is final,
	 * use {@link #configureViewResolvers} to customize view resolvers.
	 */
	protected final ViewResolverRegistry getViewResolverRegistry() {
		if (this.viewResolverRegistry == null) {
			this.viewResolverRegistry = new ViewResolverRegistry(getApplicationContext());
			configureViewResolvers(this.viewResolverRegistry);
		}
		return this.viewResolverRegistry;
	}

	/**
	 * Configure view resolution for supporting template engines.
	 * @see ViewResolverRegistry
	 */
	protected void configureViewResolvers(ViewResolverRegistry registry) {
	}


	private static final class EmptyHandlerMapping extends AbstractHandlerMapping {

		@Override
		public Mono<Object> getHandlerInternal(ServerWebExchange exchange) {
			return Mono.empty();
		}
	}


	private static final class NoOpValidator implements Validator {

		@Override
		public boolean supports(Class<?> clazz) {
			return false;
		}

		@Override
		public void validate(@Nullable Object target, Errors errors) {
		}
	}

}
