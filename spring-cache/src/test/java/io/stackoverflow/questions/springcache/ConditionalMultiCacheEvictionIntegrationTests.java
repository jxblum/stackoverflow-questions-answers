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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests for Conditional, Multi-Cache, Eviction using Spring's Cache Abstraction.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see <a href="https://stackoverflow.com/questions/65938530/cacheevict-on-conditional-annotation-spring-caching">CacheEvict on conditional annotation - Spring Caching</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class ConditionalMultiCacheEvictionIntegrationTests {

	private Cache adminCache;
	private Cache usersCache;
	private Cache guestsCache;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private UserService userService;

	@Before
	public void setup() {

		assertThat(this.userService).isNotNull();
		assertThat(this.cacheManager).isNotNull();
		assertThat(this.cacheManager.getCacheNames()).containsExactlyInAnyOrder("Admin", "Users", "Guests");

		this.adminCache = this.cacheManager.getCache("Admin");
		this.usersCache = this.cacheManager.getCache("Users");
		this.guestsCache = this.cacheManager.getCache("Guests");

		assertCacheConfigurationAndState(this.adminCache, "Admin");
		assertCacheConfigurationAndState(this.usersCache, "Users");
		assertCacheConfigurationAndState(this.guestsCache, "Guests");
	}

	private void assertCacheConfigurationAndState(@NonNull Cache cache, @NonNull String cacheName) {

		assertThat(cache).isNotNull();
		assertThat(cache.getName()).isEqualTo(cacheName);

		assertCachesAreEmpty(cache);
	}

	private void assertCachesContainUser(@NonNull User user, @NonNull Cache... caches) {

		Arrays.stream(caches)
			.filter(Objects::nonNull)
			.forEach(cache -> assertThat(cache.get(user.getName(), User.class)).isEqualTo(user));
	}

	private void assertCachesAreEmpty(@NonNull Cache... caches) {

		Arrays.stream(caches)
			.filter(Objects::nonNull)
			.forEach(cache -> {
				assertThat(cache.getNativeCache()).isInstanceOf(Map.class);
				assertThat((Map<?, ?>) cache.getNativeCache()).isEmpty();
			});
	}

	@Test
	public void conditionalCacheAndCacheEvictApplicationServiceMethodOperations() {

		User jonDoe = User.as("jonDoe");

		this.userService.process(null);

		assertThat(this.userService.wasProcessCalled()).isTrue();
		assertCachesAreEmpty(this.adminCache);

		this.userService.process(jonDoe);

		assertThat(this.userService.wasProcessCalled()).isTrue();
		assertCachesContainUser(jonDoe, this.adminCache, this.usersCache, this.guestsCache);

		this.userService.revoke(null);

		assertThat(this.userService.wasRevokeCalled()).isTrue();
		assertCachesContainUser(jonDoe, this.adminCache, this.usersCache, this.guestsCache);

		this.userService.revoke(jonDoe);

		assertThat(this.userService.wasRevokeCalled()).isTrue();
		assertCachesAreEmpty(this.adminCache);
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		ConcurrentMapCacheManager cacheManager() {

			ConcurrentMapCacheManager cacheManager =
				new ConcurrentMapCacheManager("Admin", "Users", "Guests");

			cacheManager.setAllowNullValues(true);

			return cacheManager;
		}

		@Bean
		UserService userService() {
			return new UserService();
		}
	}

	@Service
	public static class UserService {

		private final AtomicBoolean processCalled = new AtomicBoolean(false);
		private final AtomicBoolean revokeCalled = new AtomicBoolean(false);

		public boolean wasProcessCalled() {
			return this.processCalled.compareAndSet(true, false);
		}

		public boolean wasRevokeCalled() {
			return this.revokeCalled.compareAndSet(true, false);
		}

		@Cacheable(cacheNames = { "Admin", "Users", "Guests" },
			key = "#user.name", condition = "#user != null && #user.name != null")
		public User process(User user) {
			this.processCalled.set(true);
			return user;
		}

		@CacheEvict(cacheNames = { "Admin", "Users", "Guests"},
			key = "#user.name", condition = "#user != null && #user.name != null")
		public User revoke(User user) {
			this.revokeCalled.set(true);
			return user;
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
}
