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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} class used to enable Spring {@link CacheManager} and {@link Cache} decoration.
 *
 * @author John Blum
 * @see java.util.function.Function
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see <a href="https://en.wikipedia.org/wiki/Decorator_pattern">Decorator Software Design Pattern</a>
 * @since 1.0.0
 */
@Configuration
@SuppressWarnings("unused")
public class CacheDecorationConfiguration {

	@Bean
	public CacheManagerDecoratingBeanPostProcessor cacheManagerDecoratingBeanPostProcessor(
			@Qualifier("cacheManagerDecorator") Function<CacheManager, CacheManager> cacheManagerDecoratorFunction) {

		return CacheManagerDecoratingBeanPostProcessor.from(cacheManagerDecoratorFunction);
	}
}
