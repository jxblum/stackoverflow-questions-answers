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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.shiro.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.gemfire.cache.GemfireCache;
import org.springframework.data.gemfire.cache.config.EnableGemfireCaching;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.EnableCachingDefinedRegions;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

import io.stackoverflow.questions.answers.spring.cache.AbstractCacheDelegate;
import io.stackoverflow.questions.answers.spring.cache.AbstractCacheManagerDelegate;
import io.stackoverflow.questions.answers.spring.cache.CacheManagerDecoratingBeanPostProcessor;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests demonstrating the use of a custom {@link CacheManager} and {@link Cache}
 * to evict a collection of keys matching a Regular Expression (REGEX).
 *
 * @author John Blum
 * @see java.util.Map
 * @see java.util.regex.Pattern
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.CacheableService
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.test.context.ActiveProfiles
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@ActiveProfiles("ConcurrentMapCache")
//@ActiveProfiles("ApacheGeodeCache")
@SuppressWarnings("unused")
public class CacheEvictionWithRegexIntegrationTests {

	private static final String CACHE_NAME = "Players";

	@Autowired
	@Getter(AccessLevel.PRIVATE)
	private GameService gameService;

	@Before
	public void setup() {

		assertThat(getGameService()).isNotNull();
		assertThat(getGameService().isCacheMiss()).isFalse();
	}

	@Test
	public void cacheKeyEvictionBasedOnRegexFunctionCorrectly() {

		// Create Players for the game...

		Player bluePlayer = Player.as("bluePlayer");
		Player greenPlayer = Player.as("greenPlayer");
		Player redPlayerOne = Player.as("redPlayerOne");
		Player redPlayerTwo = Player.as("redPlayerTwo");

		// Cache the Players' (current) Location...

		Location redPlayerOneLocation = assertCacheMissAndReturnLocationOf(redPlayerOne);
		Location redPlayerTwoLocation = assertCacheMissAndReturnLocationOf(redPlayerTwo);
		Location greenPlayerLocation = assertCacheMissAndReturnLocationOf(greenPlayer);
		Location bluePlayerLocation = assertCacheMissAndReturnLocationOf(bluePlayer);

		// Test the state of the cache
		assertThat(assertCacheHitAndReturnLocationOf(redPlayerOne)).isEqualTo(redPlayerOneLocation);
		assertThat(assertCacheHitAndReturnLocationOf(redPlayerTwo)).isEqualTo(redPlayerTwoLocation);
		assertThat(assertCacheHitAndReturnLocationOf(greenPlayer)).isEqualTo(greenPlayerLocation);
		assertThat(assertCacheHitAndReturnLocationOf(bluePlayer)).isEqualTo(bluePlayerLocation);

		// Evict a single Player from the cache (kill a Player in the game).
		getGameService().kill(bluePlayer);

		assertThat(assertCacheMissAndReturnLocationOf(bluePlayer)).isNotEqualTo(bluePlayerLocation);

		// Evict all red Players from the cache (kill all red Players in the game) using REGEX 'red.*'
		getGameService().kill("red.*");

		Location newRedPlayerOneLocation = assertCacheMissAndReturnLocationOf(redPlayerOne);

		assertThat(newRedPlayerOneLocation).isNotEqualTo(redPlayerOneLocation);
		assertThat(assertCacheMissAndReturnLocationOf(redPlayerTwo)).isNotEqualTo(redPlayerTwoLocation);

		// 1 final test to verify the cache is used once again
		assertThat(assertCacheHitAndReturnLocationOf(redPlayerOne)).isEqualTo(newRedPlayerOneLocation);
	}

	private Location assertCacheHitAndReturnLocationOf(@NonNull Player player) {
		return assertCacheHitAndReturnLocationOf(getGameService(), player);
	}

	private Location assertCacheHitAndReturnLocationOf(@NonNull GameService gameService, @NonNull Player player) {

		Location location = gameService.locationOf(player);

		assertThat(gameService.isCacheMiss()).isFalse();

		return location;
	}

	private Location assertCacheMissAndReturnLocationOf(@NonNull Player player) {
		return assertCacheMissAndReturnLocationOf(getGameService(), player);
	}

	private Location assertCacheMissAndReturnLocationOf(@NonNull GameService gameService, @NonNull Player player) {

		Location location = gameService.locationOf(player);

		assertThat(gameService.isCacheMiss()).isTrue();
		// Assessing the state of cacheMiss should reset the bit/flag.
		assertThat(gameService.isCacheMiss()).isFalse();

		return location;
	}

	@Service
	static class GameService extends CacheableService {

		@Getter(AccessLevel.PROTECTED)
		private final Random random = new Random(System.currentTimeMillis());

		@Cacheable(CACHE_NAME)
		public @NonNull Location locationOf(@NonNull Player player) {

			setCacheMiss();

			int x = getRandom().nextInt(100);
			int y = getRandom().nextInt(100);

			return Location.at(x, y);
		}

		@CacheEvict(CACHE_NAME)
		public void kill(@NonNull Object possibleRegexKeyOrPlayer) { }

	}

	@Getter
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "as")
	static class Player {

		@lombok.NonNull
		private final String name;

		@Override
		public String toString() {
			return getName();
		}
	}

	@Getter
	@ToString
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "at")
	static class Location {

		private final int x;
		private final int y;

	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		BeanPostProcessor cacheManagerDecoratingBeanPostProcessor(
			@Autowired(required = false) BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			return CacheManagerDecoratingBeanPostProcessor
				.from(cacheManager -> RegexBasedEvictionCacheManager.from(cacheManager, cacheKeysFunction));
		}

		@Bean
		GameService gameService() {
			return new GameService();
		}
	}

	@Configuration
	@Profile("ConcurrentMapCache")
	static class ConcurrentMapTestConfiguration {

		@Bean
		CacheManager cacheManager() {
			return new ConcurrentMapCacheManager(CACHE_NAME);
		}
	}

	@Configuration
	@Profile("ApacheGeodeCache")
	@EnableGemfireCaching
	@ClientCacheApplication(name = "CacheEvictionWithRegexUsingApacheGeode")
	@EnableCachingDefinedRegions(clientRegionShortcut = ClientRegionShortcut.LOCAL)
	static class GeodeTestConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
		BiFunction<Cache, Object, List<Object>> cacheKeysFunction() {

			return (cache, key) -> {

				Assert.isInstanceOf(GemfireCache.class, cache);

				Region<Object, Object> region = ((GemfireCache) cache).getNativeCache();

				return new ArrayList<>(isNonLocalClient(region)
					? region.keySetOnServer()
					: region.keySet());
			};
		}

		private boolean isNonLocalClient(@NonNull Region<?, ?> region) {
			return region.getRegionService() instanceof ClientCache
				&& StringUtils.hasText(region.getAttributes().getPoolName());
		}
	}

	@Configuration
	@Profile("RedisCache")
	// NOTE: I am not that familiar with Redis, so I am not quite certain what I am doing here is 100% correct.
	// Best to let Spring Boot handle all configuration for Redis.
	// However, I am not using Spring Boot in my tests and this test class is purely for demonstration purposes.
	static class RedisTestConfiguration {

		@Bean
		RedisConnectionFactory connectionFactory() {
			return new LettuceConnectionFactory();
		}

		@Bean
		RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

			RedisTemplate<Object, Object> template = new RedisTemplate<>();

			template.setConnectionFactory(connectionFactory);

			return template;
		}

		@Bean
		CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
			return RedisCacheManager.builder(connectionFactory)
				.initialCacheNames(Collections.singleton(CACHE_NAME))
				.build();
		}

		@Bean
		BiFunction<Cache, Object, List<Object>> cacheKeysFunction(RedisTemplate<Object, Object> redisTemplate) {

			return (cache, key) -> {

				Assert.isInstanceOf(RedisCache.class, cache);

				return new ArrayList<>(redisTemplate.opsForHash().keys(cache.getName()));
			};
		}
	}

	static class RegexBasedEvictionCacheManager extends AbstractCacheManagerDelegate {

		static @NonNull RegexBasedEvictionCacheManager from(@NonNull CacheManager cacheManager,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			return new RegexBasedEvictionCacheManager(cacheManager, cacheKeysFunction);
		}

		@Getter(AccessLevel.PROTECTED)
		private final BiFunction<Cache, Object, List<Object>> cacheKeysFunction;

		RegexBasedEvictionCacheManager(@NonNull CacheManager cacheManager,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			super(cacheManager);

			this.cacheKeysFunction = cacheKeysFunction;
		}

		/**
		 * Looks up a {@link Cache} with the given {@link String name} managed by this {@link CacheManager}.
		 *
		 * If the {@link Cache} for the given {@link String name} exists (i.e. is not {@literal null}),
		 * then the {@link Cache} is {@literal decorated} with {@link RegexBasedEvictionCache}.
		 *
		 * @param name {@link String} containing the {@literal name} of the {@link Cache} to lookup with
		 * this {@link CacheManager}.
		 * @return the {@literal decorated} {@link Cache}.
		 * @see org.springframework.cache.Cache
		 * @see RegexBasedEvictionCache
		 * @see #getCacheManager()
		 */
		@Override
		public Cache getCache(@NonNull String name) {

			Cache cache = getCacheManager().getCache(name);

			return cache != null
				? RegexBasedEvictionCache.from(cache, getCacheKeysFunction())
				: cache;
		}
	}

	static class RegexBasedEvictionCache extends AbstractCacheDelegate {

		static @NonNull RegexBasedEvictionCache from(@NonNull Cache cache,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			return new RegexBasedEvictionCache(cache, cacheKeysFunction);
		}

		@Getter(AccessLevel.PROTECTED)
		private final RegexCacheKeysResolver cacheKeysResolver;

		RegexBasedEvictionCache(@NonNull Cache cache,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			super(cache);

			this.cacheKeysResolver = RegexCacheKeysResolver.from(cacheKeysFunction);
		}

		/**
		 * Evicts the {@link Cache} entry with the given {@link Object key}.
		 *
		 * This {@literal evict(key)} method uses a {@link RegexCacheKeysResolver} to handle the case
		 * where the given {@link Object key} represents a Regular Expression (REGEX) {@link Pattern}
		 * to identify more than 1 key in order to evict multiple entries from the {@link Cache}.
		 *
		 * @param key {@link Object} identifying the {@link Cache} entry to evict. It is possible for
		 * the {@link Object key} to represent a Regular Expression (REGEX) {@link Pattern}
		 * to identify multiple {@link Cache} entries to evict in a single operation.
		 * @see #getCacheKeysResolver()
		 * @see #getCache()
		 */
		@Override
		public void evict(@NonNull Object key) {

			List<Object> keys = getCacheKeysResolver().resolve(getCache(), key);

			keys.forEach(super::evict);
		}
	}

	static class RegexCacheKeysResolver {

		/**
		 * Factory method used to construct a new instance of the {@link RegexCacheKeysResolver} initialized with
		 * the given {@link BiFunction} used to get a {@link List} of all available {@link Object keys}
		 * from the {@link Cache}.
		 *
		 * @param cacheKeysFunction {@link BiFunction} used to get a {@link List} of all available {@link Object keys)
		 * from the {@link Cache}.
		 * @return a new instance of {@link RegexCacheKeysResolver}.
		 * @see org.springframework.cache.Cache
		 * @see #RegexCacheKeysResolver(Cache)
		 * @see java.util.function.BiFunction
		 * @see java.util.List
		 */
		static @NonNull RegexCacheKeysResolver from(@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {
			return new RegexCacheKeysResolver(cacheKeysFunction);
		}

		protected static final BiFunction<Cache, Object, List<Object>> DEFAULT_CACHE_TO_KEYS_FUNCTION =
			(cache, key) -> {

				Object nativeCache = cache.getNativeCache();

				return nativeCache instanceof Map<?, ?>
					? new ArrayList<>(((Map<?, ?>) nativeCache).keySet())
					: Collections.singletonList(key);
			};

		@Getter(AccessLevel.PROTECTED)
		private final BiFunction<Cache, Object, List<Object>> cacheKeysFunction;

		/**
		 * Constructs a new instance of the {@link RegexCacheKeysResolver} initialized with the given {@link BiFunction}
		 * used to get a {@link List} of all available {@link Object keys} from the {@link Cache}.
		 *
		 * @param cacheKeysFunction {@link BiFunction} used to get a {@link List} of all available {@link Object keys)
		 * from the {@link Cache}.
		 * @see org.springframework.cache.Cache
		 * @see java.util.function.BiFunction
		 * @see java.util.List
		 */
		RegexCacheKeysResolver(@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction) {

			this.cacheKeysFunction = cacheKeysFunction != null
				? cacheKeysFunction
				: DEFAULT_CACHE_TO_KEYS_FUNCTION;
		}

		/**
		 * Resolves a {@link List} of {@link Object keys} from the {@link Cache} matching the Regular Expression (REGEX)
		 * {@link Pattern} specified in the given argument to the {@code key} method parameter.
		 *
		 * If the {@link Object key} method parameter does not represent a Regular Expression (REGEX) {@link Pattern}
		 * (for example, by throwing {@link PatternSyntaxException}), then this resolve method returns the given
		 * {@link Object key} in a {@literal Singleton} {@link List}.
		 *
		 * @param cache {@link Cache} from which to get a {@link List} of all the matching {@link Object keys}.
		 * @param key {@link Object} representing a Regular Expression {@link Pattern} or possibly just a single,
		 * individual {@literal key} from the {@link Cache}.
		 * @return a {@link List} of all @link Object keys} from the {@link Cache} matching the Regular Expression
		 * (REGEX) {@link Pattern} specified in the given argument to the {@code key} method parameter.
		 * If the {@code key} argument does not represent a Regular Expression (REGEX) {@link Pattern}, then return
		 * the given {@link Object key} in a {@literal Singleton} {@link List}.
		 * @see org.springframework.cache.Cache
		 * @see #isMatch(Object, Pattern)
		 * @see java.util.regex.Pattern
		 * @see java.util.List
		 */
		List<Object> resolve(@NonNull Cache cache, @NonNull Object key) {

			try {

				// Only if the Regular Expression (REGEX) Pattern compiles should we go onto get all keys from the cache,
				// which, depending on the cache implementation, could be expensive (e.g. memory, network, etc).
				Pattern keyPattern = Pattern.compile(String.valueOf(key));

				// The Pattern compiled so now go get all the available keys in the cache; i.e. all keys
				// that are mapped to a value, stored in the cache.
				List<Object> allKeys = getCacheKeysFunction().apply(cache, key);

				// Filter all Cache keys based on its String representation matching the Regular Expression
				// (REGEX) Pattern and return only those cache keys that match.
				return allKeys.stream()
					.filter(keyFromCache -> isMatch(keyFromCache, keyPattern))
					.collect(Collectors.toList());
			}
			catch (PatternSyntaxException ignore) {
				return Collections.singletonList(key);
			}
		}

		private boolean isMatch(@Nullable Object keyFromCache, @NonNull Pattern pattern) {
			return pattern.matcher(String.valueOf(keyFromCache)).matches();
		}
	}
}
