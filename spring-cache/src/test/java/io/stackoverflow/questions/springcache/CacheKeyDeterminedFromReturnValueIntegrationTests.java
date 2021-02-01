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
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests asserting a cache key determined from the return value
 * of a {@link Cacheable} {@link Service} method.
 *
 * Technically, Spring's Cache Abstraction evaluates (and holds on to) the cache key before the {@link Cacheable}
 * {@link Service} method is invoked. Therefore, it is not possible to compute the cache key from the return value
 * when annotating {@link Service} methods with {@link Cacheable}. Use {@link CachePut} instead.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.CachePut
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.concurrent.ConcurrentMapCache
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
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
public class CacheKeyDeterminedFromReturnValueIntegrationTests {

	private Cache usersCache;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private UserService userService;

	private static void log(String message, Object... args) {
		System.err.printf(message, args);
		System.err.flush();
	}

	@Before
	@SuppressWarnings("all")
	public void setup() {

		assertThat(this.userService).isNotNull();
		assertThat(this.cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);

		this.usersCache = this.cacheManager.getCache("UsersCache");

		assertThat(this.usersCache).isInstanceOf(ConcurrentMapCache.class);
		assertThat(this.usersCache.getName()).isEqualTo("UsersCache");
		assertThat(this.usersCache.getNativeCache()).isInstanceOf(ConcurrentMap.class);
	}

	@After
	@SuppressWarnings("unchecked")
	public void tearDown() {

		User jonDoe = User.as("Jon Doe");

		Object usersNativeCache = this.usersCache.getNativeCache();

		log("'UsersCache' is [%s]%n", usersNativeCache);

		assertThat(usersNativeCache).isInstanceOf(ConcurrentMap.class);
		assertThat((ConcurrentMap<String, User>) usersNativeCache).hasSize(1);
		//assertThat((ConcurrentMap<String, User>) usersNativeCache).containsKeys(jonDoe.getName());
		assertThat((ConcurrentMap<String, User>) usersNativeCache).containsKeys("DEFAULT_KEY");
		//assertThat(((ConcurrentMap<String, User>) usersNativeCache).get(jonDoe.getName())).isEqualTo(jonDoe);
		assertThat(((ConcurrentMap<String, User>) usersNativeCache).get("DEFAULT_KEY")).isEqualTo(jonDoe);
	}

	@Test
	public void cachingIsSuccessful() {

		assertThat(this.userService.isCacheMiss()).isFalse();
		assertThat(this.userService.findFirst()).isEqualTo(User.as("Jon Doe"));
		assertThat(this.userService.isCacheMiss()).isTrue();
		assertThat(this.userService.findFirst()).isEqualTo(User.as("Jon Doe"));
		assertThat(this.userService.isCacheMiss()).isFalse();
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		ConcurrentMapCacheManager cacheManager() {
			return new ConcurrentMapCacheManager("UsersCache");
		}

		@Bean
		UserService userService() {
			return new UserService();
		}
	}

	@Getter
	@ToString
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "as")
	static class User {

		@lombok.NonNull
		private final String name;

	}

	@Service
	static class UserService extends CacheableService {

		// TODO: Technically, enabling caching for the findFirst() method would be more appropriately annotated
		//  with @CachePut
		//@Cacheable(cacheNames = "UsersCache", key = "#result?.name?:'DEFAULT_KEY'", condition = "#result != null")
		//@Cacheable(cacheNames = "UsersCache", key = "#result?.name?:'DEFAULT_KEY'", unless = "#result == null")
		@Cacheable(cacheNames = "UsersCache", key = "#result?.name?:'DEFAULT_KEY'")
		//@Cacheable(cacheNames = "UsersCache", key = "#result?.name", condition = "#result != null")
		//@Cacheable(cacheNames = "UsersCache", key = "#result?.name", unless = "#result == null")
		//@Cacheable(cacheNames = "UsersCache", key = "#result?.name")
		//@CachePut(cacheNames = "UsersCache", key = "#result?.name")
		public User findFirst() {
			setCacheMiss();
			return User.as("Jon Doe");
		}
	}
}
