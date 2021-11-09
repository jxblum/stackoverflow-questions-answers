/*
 * Copyright 2020-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.stackoverflow.questions.answers.spring.cache.annotation;

import java.util.function.Function;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Spring {@link BeanPostProcessor} that {@literal decorates} a Spring {@link CacheManager} bean
 * registered in the Sprig container for the Spring application.
 *
 * @author John Blum
 * @see java.util.function.Function
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.cache.CacheManager
 * @see <a href="https://en.wikipedia.org/wiki/Decorator_pattern">Decorator Software Design Pattern</a>
 * @since 1.0.0
 */
public class CacheManagerDecoratingBeanPostProcessor implements BeanPostProcessor {

	/**
	 * Factory method used to construct a new instance of {@link CacheManagerDecoratingBeanPostProcessor}
	 * initialized with the given {@link Function} used to {@literal decorate} the {@link CacheManager} bean
	 * registered in the Spring context.
	 *
	 * @param cacheManagerDecoratorFunction {@link Function} used to {@literal decorate}
	 * the registered {@link CacheManager} bean.
	 * @see #CacheManagerDecoratingBeanPostProcessor(Function)
	 * @see org.springframework.cache.CacheManager
	 * @see java.util.function.Function
	 */
	public static @NonNull CacheManagerDecoratingBeanPostProcessor from(
			@Nullable Function<CacheManager, CacheManager> cacheManagerDecoratorFunction) {

		return new CacheManagerDecoratingBeanPostProcessor(cacheManagerDecoratorFunction);
	}

	/**
	 * Using a Java {@link Function} allows multiple {@link CacheManager} decorations to be {@literal composed}.
	 * @see <a href="https://en.wikipedia.org/wiki/Composite_pattern">Composite Software Design Pattern</a>
	 */
	@Getter(AccessLevel.PROTECTED)
	private final Function<CacheManager, CacheManager> cacheManagerDecoratorFunction;

	/**
	 * Constructs a new instance of {@link CacheManagerDecoratingBeanPostProcessor} initialized with
	 * the given {@link Function} used to {@literal decorate} the {@link CacheManager} bean
	 * registered in the Spring context.
	 *
	 * @param cacheManagerDecoratorFunction {@link Function} used to {@literal decorate}
	 * the registered {@link CacheManager} bean.
	 * @see org.springframework.cache.CacheManager
	 * @see java.util.function.Function
	 */
	public CacheManagerDecoratingBeanPostProcessor(
			@Nullable Function<CacheManager, CacheManager> cacheManagerDecoratorFunction) {

		this.cacheManagerDecoratorFunction = cacheManagerDecoratorFunction != null
			? cacheManagerDecoratorFunction
			: Function.identity();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public @NonNull Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
			throws BeansException {

		return bean instanceof CacheManager
			? getCacheManagerDecoratorFunction().apply((CacheManager) bean)
			: bean;
	}
}
