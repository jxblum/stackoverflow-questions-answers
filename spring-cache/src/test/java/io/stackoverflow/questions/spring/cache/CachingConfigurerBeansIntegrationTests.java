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
package io.stackoverflow.questions.spring.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;

import io.stackoverflow.questions.answers.spring.cache.CacheException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integrations Tests testing and asserting the configuration of a {@link CacheErrorHandler} bean by the Spring container
 * when implementing the {@link CachingConfigurer} interface on a Spring application {@link Configuration} class
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.mockito.Mockito
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.boot.test.context.SpringBootTest
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.CachingConfigurer
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.interceptor.CacheErrorHandler
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.test.context.ActiveProfiles
 * @see org.springframework.test.context.junit.jupiter.SpringExtension
 * @see <a href="https://stackoverflow.com/questions/74227543/how-to-inject-a-bean-into-cacheerrorhandler">How to inject a bean into CacheErrorHandler</a>
 * @since 0.1.0
 */
@ActiveProfiles("cache-infra-beans-config")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Getter(AccessLevel.PROTECTED)
@SuppressWarnings("unused")
public class CachingConfigurerBeansIntegrationTests {

	private static final boolean DEBUG = false;

	@Autowired
	private Logger mockLogger;

	@Autowired
	private UserService userService;

	@Test
	public void cacheErrorHandlingWorksProperly() {

		User jonDoe = User.as("JonDoe");

		// User "JonDoe" is now in the "Users" Cache, triggering cache hits when requested
		getUserService().process(jonDoe);

		User cachedJonDoe = getUserService().findOrCreateUserByName("JonDoe");

		assertThat(cachedJonDoe).isEqualTo(jonDoe);
		assertThat(cachedJonDoe).isSameAs(jonDoe);
		assertThat(getUserService().isCacheMiss()).isFalse(); // CACHE HIT (counter is 1)

		verifyNoInteractions(getMockLogger()); // NO cache error yet...

		User createdJonDoe = getUserService().findOrCreateUserByName("JonDoe");

		assertThat(createdJonDoe).isEqualTo(jonDoe);
		assertThat(createdJonDoe).isNotSameAs(jonDoe); // User "JonDoe" was created due to "Users" Cache error
		assertThat(getUserService().isCacheMiss()).isTrue(); // Cache error results in findOCreateUserByName(..) UserService method invocation

		verify(getMockLogger(), times(1))
			.warn(eq("Failed to get value for key [JonDoe] in cache [Users]"), isA(CacheException.class));

		verifyNoMoreInteractions(getMockLogger());
	}

	@EnableCaching
	@SpringBootConfiguration
	//@SpringBootConfiguration(proxyBeanMethods = false)
	@Profile("cache-infra-beans-config")
	static class TestConfiguration extends MockCachingInfrastructureConfiguration implements CachingConfigurer {

		@Override
		public CacheErrorHandler errorHandler() {
			return new TestCacheErrorHandler(mockLogger());
		}

		@Bean
		Logger mockLogger() {
			return mock(Logger.class, "MockLogger");
		}

		@Bean
		UserService userService() {
			return new UserService();
		}

		@Override
		@SuppressWarnings("unchecked")
		protected BiFunction<Cache, Map<Object, Object>, Cache> decorateCache() {

			AtomicInteger counter = new AtomicInteger(0);

			return (cache, cacheMap) -> {

				if (isUsersCache(cache)) {

					Answer<Object> cacheGetAnswer = invocation -> {

						if (triggerCacheException(counter)) {
							throw new CacheException("Cache is not responding to requests");
						}

						Object value = cacheMap.get(invocation.getArgument(0));

						if (invocation.getArguments().length == 1) {
							value = new SimpleValueWrapper(value);
						}

						return value;
					};

					doAnswer(cacheGetAnswer).when(cache).get(any());
					doAnswer(cacheGetAnswer).when(cache).get(any(), any(Class.class));
				}

				return cache;
			};
		}

		private boolean isUsersCache(Cache cache) {
			return cache.getName().equals("Users");
		}

		private boolean triggerCacheException(AtomicInteger counter) {
			return counter.incrementAndGet() % 2 == 0;
		}
	}

	@RequiredArgsConstructor
	static class TestCacheErrorHandler extends SimpleCacheErrorHandler {

		@lombok.NonNull
		@Getter(AccessLevel.PACKAGE)
		private final Logger logger;

		@Override
		public void handleCacheGetError(@NonNull RuntimeException cause, @NonNull Cache cache, @NonNull Object key) {
			getLogger().warn(String.format("Failed to get value for key [%s] in cache [%s]",
				key, cache.getName()), cause);
		}
	}

	static class MockCachingInfrastructureConfiguration {

		public CacheManager cacheManager() {
			return mockCacheManager();
		}

		protected @NonNull CacheManager mockCacheManager() {

			Set<Cache> caches = new ConcurrentSkipListSet<>(Comparator.comparing(Cache::getName));

			CacheManager mockCacheManager = mock(CacheManager.class);

			doAnswer(invocation -> {

				String cacheName = invocation.getArgument(0);

				return findCacheByName(caches, cacheName)
					.orElseGet(() -> {
						Cache namedCached = mockCache(cacheName);
						caches.add(namedCached);
						return namedCached;
					});

			}).when(mockCacheManager).getCache(anyString());

			doAnswer(invocation -> caches.stream().map(Cache::getName).collect(Collectors.toSet()))
				.when(mockCacheManager).getCacheNames();

			return mockCacheManager;
		}

		@SuppressWarnings("unchecked")
		protected @NonNull Cache mockCache(@NonNull String cacheName) {

			Assert.hasText(cacheName, () -> String.format("Cache name [%s] is required", cacheName));

			Map<Object, Object> cacheMap = new ConcurrentHashMap<>();

			Cache mockCache = mock(Cache.class, cacheName);

			doReturn(cacheName).when(mockCache).getName();
			doReturn(cacheMap).when(mockCache).getNativeCache();

			// Mocked ache.get(..) ops
			doAnswer(invocation -> new SimpleValueWrapper(cacheMap.get(invocation.getArgument(0))))
				.when(mockCache).get(any());

			doAnswer(invocation -> cacheMap.get(invocation.getArgument(0)))
				.when(mockCache).get(any(), any(Class.class));

			doAnswer(invocation -> {

				Object key = invocation.getArgument(0);
				Object value = cacheMap.get(key);

				try {
					return value != null ? value :
						invocation.getArgument(1, Callable.class).call();
				}
				catch (Exception cause) {
					throw new Cache.ValueRetrievalException(key, invocation.getArgument(1), cause);
				}
			}).when(mockCache).get(any(), any(Callable.class));

			// Mocked Cache.put(..) ops
			doAnswer(invocation -> cacheMap.put(invocation.getArgument(0), invocation.getArgument(1)))
				.when(mockCache).put(any(), any());

			doAnswer(invocation -> cacheMap.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)))
				.when(mockCache).putIfAbsent(any(), any());

			return decorateCache().apply(mockCache, cacheMap);
		}

		protected BiFunction<Cache, Map<Object, Object>, Cache> decorateCache() {
			return (cache, cacheMap) -> cache;
		}

		private Optional<Cache> findCacheByName(@NonNull Set<Cache> caches, @NonNull String cacheName) {

			Optional<Cache> cacheByName = caches.stream()
				.filter(cache -> cache.getName().equals(cacheName))
				.findFirst();

			cacheByName.ifPresentOrElse(cache -> logDebug("Cache with name [%s] was found%n", cache.getName()),
				() -> logDebug("Cache with name [%s] was not found%n", cacheName));

			return cacheByName;
		}

		private void logDebug(@NonNull String message, Object... args) {
			if (DEBUG) {
				System.err.printf(message, args);
				System.err.flush();
			}
		}
	}

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "as")
	static class User {

		@lombok.NonNull
		private final String name;

	}

	@Service
	static class UserService {

		private final AtomicBoolean cacheMiss = new AtomicBoolean(false);

		public boolean isCacheMiss() {
			return this.cacheMiss.getAndSet(false);
		}

		@Cacheable("Users")
		public @NonNull User findOrCreateUserByName(@NonNull String name) {
			this.cacheMiss.set(true);
			return User.as(name);
		}

		@CachePut(cacheNames = "Users", key = "#user.name")
		public @NonNull User process(@NonNull User user) {
			return user;
		}
	}
}
