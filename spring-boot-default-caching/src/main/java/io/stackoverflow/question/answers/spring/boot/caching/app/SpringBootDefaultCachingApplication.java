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
package io.stackoverflow.question.answers.spring.boot.caching.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * {@link SpringBootApplication} used to assert Spring Boot's default, auto-configured caching behavior.
 *
 * @author John Blum
 * @see org.springframework.boot.ApplicationRunner
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see org.springframework.boot.builder.SpringApplicationBuilder
 * @see org.springframework.cache.annotation.EnableCaching
 * @since 1.0.0
 */
@SpringBootApplication
@EnableCaching
public class SpringBootDefaultCachingApplication {

	private static final boolean DEBUG = false;

	public static void main(String[] args) {

		new SpringApplicationBuilder(SpringBootDefaultCachingApplication.class)
			.web(WebApplicationType.NONE)
			.properties(
				String.format("debug=%s", DEBUG),
				"spring.cache.cache-names=Users,TestCache,MockCache"
			)
			.build()
			.run(args);
	}

	@Bean
	ApplicationRunner cachingRunner(CacheManager cacheManager) {

		return args -> {

			assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
			assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("Users", "TestCache", "MockCache");

			System.err.println("SUCCESS!!");
		};
	}
}
