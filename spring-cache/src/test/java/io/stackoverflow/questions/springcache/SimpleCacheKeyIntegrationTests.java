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
package io.stackoverflow.questions.springcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integrations Tests asserting the implicit creation of a cache key (i.e. {@link SimpleKey}
 * by Spring's Cache Abstraction.
 *
 * @author John Blum
 * @see java.util.concurrent.ConcurrentMap
 * @see org.junit.Test
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.concurrent.ConcurrentMapCache
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
 * @see org.springframework.cache.interceptor.SimpleKey
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.stereotype.Service
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see <a href="https://stackoverflow.com/questions/65981194/spring-caching-what-is-the-cache-key-if-there-is-no-key-as-well-as-parameter">Spring Caching. what is the cache key if there is no key as well as parameter</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class SimpleCacheKeyIntegrationTests {

	private Cache helloCache;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private TestCacheableService cacheableService;

	private static void log(String message, Object... args) {
		System.err.printf(message, args);
		System.err.flush();
	}

	@Before
	@SuppressWarnings("all")
	public void setup() {

		assertThat(this.cacheableService).isNotNull();
		assertThat(this.cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);

		this.helloCache = this.cacheManager.getCache("HelloCache");

		assertThat(this.helloCache).isInstanceOf(ConcurrentMapCache.class);
		assertThat(this.helloCache.getName()).isEqualTo("HelloCache");
		assertThat(this.helloCache.getNativeCache()).isInstanceOf(ConcurrentMap.class);
	}

	@After
	@SuppressWarnings("unchecked")
	public void tearDown() {

		Object helloNativeCache = this.helloCache.getNativeCache();

		log("'HelloCache' is [%s]", helloNativeCache);

		assertThat(helloNativeCache).isInstanceOf(ConcurrentMap.class);
		assertThat((ConcurrentMap<Object, String>) helloNativeCache).hasSize(1);
		assertThat(((ConcurrentMap<Object, String>) helloNativeCache).get(SimpleKey.EMPTY)).isEqualTo("Hello!");
	}

	@Test
	public void cachingIsDisabledForAtCacheableServiceMethodWithNoParameters() {

		assertThat(this.cacheableService.isCacheMiss()).isFalse();
		assertThat(this.cacheableService.hello()).isEqualTo("Hello!");
		assertThat(this.cacheableService.isCacheMiss()).isTrue();
		assertThat(this.cacheableService.hello()).isEqualTo("Hello!");
		assertThat(this.cacheableService.isCacheMiss()).isFalse();
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		ConcurrentMapCacheManager cacheManager() {
			return new ConcurrentMapCacheManager("HelloCache");
		}

		@Bean
		TestCacheableService cacheableService() {
			return new TestCacheableService();
		}
	}

	@Service
	static class TestCacheableService extends CacheableService {

		@Cacheable("HelloCache")
		public String hello() {
			setCacheMiss();
			return "Hello!";
		}
	}
}
