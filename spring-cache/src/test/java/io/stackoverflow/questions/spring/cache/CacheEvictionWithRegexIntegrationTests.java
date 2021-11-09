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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.gemfire.cache.GemfireCache;
import org.springframework.data.gemfire.cache.config.EnableGemfireCaching;
import org.springframework.data.gemfire.config.annotation.EnableCachingDefinedRegions;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.geode.boot.autoconfigure.ClientCacheAutoConfiguration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.stackoverflow.questions.answers.spring.cache.AbstractCacheDelegate;
import io.stackoverflow.questions.answers.spring.cache.AbstractCacheManagerDelegate;
import io.stackoverflow.questions.answers.spring.cache.annotation.EnableCacheDecoration;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests demonstrating the use of a custom {@link CacheManager} and {@link Cache}
 * to evict a collection of keys matching a Regular Expression (REGEX).
 *
 * I like *Ori's answer because it encapsulates the logic in AOP Advice, is a pretty easy,
 * simple and a quick solution to implement, and the approach can be used with different
 * *caching providers*, with just a bit of extra work/code.
 *
 * Alternatively, users who may have complex application architectures using multiple *caching providers*,
 * which can be common when the application uses different types of caches across different layers of
 * the application (e.g. web vs. service vs. data access layers), or when different parts of the application
 * have different SLAs.
 *
 * @author John Blum
 * @see java.util.Map
 * @see java.util.regex.Pattern
 * @see org.springframework.boot.autoconfigure.EnableAutoConfiguration
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
 * @see <a href="https://stackoverflow.com/questions/69829332/is-it-possible-to-cacheevict-keys-that-match-a-pattern">Is it possible to @CacheEvict keys that match a pattern</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@ActiveProfiles({ "caching", "simple-cache" })
//@ActiveProfiles({ "caching", "redis-cache" })
//@ActiveProfiles({ "caching", "apache-geode-cache" })
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

		// Evict a single Player from the cache (respawn a Player in the game).
		getGameService().respawn(bluePlayer);

		assertThat(assertCacheMissAndReturnLocationOf(bluePlayer)).isNotEqualTo(bluePlayerLocation);

		// Evict all red Players from the cache (respawn all red Players in the game) using REGEX 'red.*'
		getGameService().respawn("red.*");

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

	// APPLICATION DOMAIN TYPES

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
		public void respawn(@NonNull Object possibleRegexKeyOrPlayer) { }

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
	static class Location implements Serializable {

		private final int x;
		private final int y;

	}

	// TEST CONFIGURATION

	@Configuration
	@EnableCaching
	@EnableCacheDecoration
	static class TestConfiguration {

		@Bean
		Function<CacheManager, CacheManager> cacheManagerDecorator(
				@Autowired(required = false) BiFunction<Cache, Object, List<Object>> cacheKeysFunction,
				@Autowired(required = false) BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction) {

			return cacheManager -> RegexBasedEvictionCacheManager
				.from(cacheManager, cacheKeysFunction, cacheEvictionFunction);
		}

		@Bean
		GameService gameService() {
			return new GameService();
		}
	}

	@Configuration
	@Profile("simple-cache")
	@EnableAutoConfiguration(exclude = { RedisAutoConfiguration.class, ClientCacheAutoConfiguration.class })
	// NOTE: No extra logic is required to enable REGEX support in the ConcurrentMap-based caching provider
	// implementation. The supportive caching infrastructure has all it needs.
	static class SimpleCacheTestConfiguration { }

	@Configuration
	@Profile("redis-cache")
	@EnableAutoConfiguration(exclude = ClientCacheAutoConfiguration.class)
	static class RedisTestConfiguration {

		private static final String REDIS_GLOB_PATTERN_SPECIAL_CHARACTERS = "?*^-\\";

		@Bean
		RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
			return builder -> builder.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().disableKeyPrefix());
		}

		/**
		 * Using the {@link org.springframework.data.redis.core.RedisTemplate}, you can use
		 * a {@link org.springframework.data.redis.connection.RedisConnection} in a callback to get all keys:
		 *
		 * <code>
		 *  return (cache, key) -> {
		 *
		 *  	RedisCallback<List<Object>> getAllKeys =
		 *  		connection -> new ArrayList<>(connection.keys(String.valueOf(key).getBytes()));
		 *
		 *		return redisTemplate.execute(getAllKeys);
		 *
		 * };
		 * </code>
		 */
		@Bean
		BiFunction<Cache, Object, List<Object>> cacheKeysFunction() {
			return (cache, key) -> Collections.singletonList(key);
		}

		@Bean
		BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction() {

			return (cache, keys) -> {

				String key = toGlobStylePattern(String.valueOf(keys.get(0)));

				((RedisCache) cache).getNativeCache().clean(cache.getName(), key.getBytes());

				return true;
			};
		}

		private @NonNull String toGlobStylePattern(@NonNull String pattern) {

			StringBuilder globPattern = new StringBuilder();

			for (char c : pattern.toCharArray()) {
				if (isAllowedRedisGlobStylePatternCharacter(c)) {
					globPattern.append(c);
				}
			}

			return globPattern.toString();
		}

		private boolean isAllowedRedisGlobStylePatternCharacter(char c) {
			return Character.isAlphabetic(c) || REDIS_GLOB_PATTERN_SPECIAL_CHARACTERS.contains(String.valueOf(c));
		}

		/**
		 * <code>
		 * Optional.ofNullable(event)
		 *	.map(ContextRefreshedEvent::getApplicationContext)
		 *	.map(applicationContext -> applicationContext.getBean(CacheManager.class))
		 *	.map(cacheManager -> cacheManager.getCache(CACHE_NAME))
		 *	.filter(RegexBasedEvictionCache.class::isInstance)
		 *	.map(RegexBasedEvictionCache.class::cast)
		 *	.map(RegexBasedEvictionCache::getCache)
		 *	.filter(RedisCache.class::isInstance)
		 *	.map(RedisCache.class::cast)
		 *	.map(Cache::getNativeCache)
		 *	.filter(RedisCacheWriter.class::isInstance)
		 *	.map(RedisCacheWriter.class::cast)
		 *	.ifPresent(redisCacheWriter -> redisCacheWriter.clean(CACHE_NAME, "*".getBytes()));
		 * </code>
		 * @param event
		 */
		@EventListener(ContextRefreshedEvent.class)
		@SuppressWarnings("all")
		public void cleanRedisCacheOnStartup(@NonNull ContextRefreshedEvent event) {
			RedisCacheWriter.nonLockingRedisCacheWriter(event.getApplicationContext()
				.getBean(RedisConnectionFactory.class))
					.clean(CACHE_NAME, "*".getBytes());
		}
	}

	@Configuration
	@Profile("apache-geode-cache")
	@EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
	@EnableCachingDefinedRegions(clientRegionShortcut = ClientRegionShortcut.LOCAL)
	@EnableGemfireCaching
	static class ApacheGeodeTestConfiguration {

		@Bean
		@SuppressWarnings("unchecked")
			// NOTE: Extra logic is required to handle the difference between a {@literal peer} {@link Cache}
			// and a {@link ClientCache} in Apache Geode when resolving all keys in the backing cache {@link Region}.
		BiFunction<Cache, Object, List<Object>> cacheKeysFunction() {

			return (cache, key) -> {

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

		@Bean
		@SuppressWarnings("unchecked")
		// NOTE: An optimal Apache Geode cache Region operation for evicting all matching keys.
		// Apache Geode does not support evicting keys based on a REGEX Pattern, unlike Redis.
		BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction() {
			return (cache, keys) -> {
				((Region<Object, ?>) cache.getNativeCache()).removeAll(keys);
				return true;
			};
		}
	}

	// INFRASTRUCTURE COMPONENTS

	/**
	 * Spring {@link CacheManager} implementation that uses a Regular Expression (REGEX) {@link Pattern}
	 * to match cache entries by key for eviction.
	 *
	 * @see io.stackoverflow.questions.answers.spring.cache.AbstractCacheManagerDelegate
	 */
	static class RegexBasedEvictionCacheManager extends AbstractCacheManagerDelegate {

		static @NonNull RegexBasedEvictionCacheManager from(@NonNull CacheManager cacheManager,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction,
				@Nullable BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction) {

			return new RegexBasedEvictionCacheManager(cacheManager, cacheKeysFunction, cacheEvictionFunction);
		}

		// BiFunction that accepts a Cache, the original Key and returns a List of Keys.
		@Getter(AccessLevel.PROTECTED)
		private final BiFunction<Cache, Object, List<Object>> cacheKeysFunction;

		// BiFunction that accepts a Cache and List of Keys to evict, then returns a boolean value
		// indicating whether the eviction was a success.
		@Getter(AccessLevel.PROTECTED)
		private final BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction;

		private final Map<Cache, RegexBasedEvictionCache> decoratedCacheMap = new ConcurrentHashMap<>();

		RegexBasedEvictionCacheManager(@NonNull CacheManager cacheManager,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction,
				@Nullable BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction) {

			super(cacheManager);

			this.cacheKeysFunction = cacheKeysFunction;
			this.cacheEvictionFunction = cacheEvictionFunction;
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
		public @Nullable Cache getCache(@NonNull String name) {

			Cache cache = getCacheManager().getCache(name);

			return cache != null
				? resolveRegexBasedEvictionCache(cache)
				: cache;
		}

		private Cache resolveRegexBasedEvictionCache(@NonNull Cache cache) {
			return this.decoratedCacheMap.computeIfAbsent(cache, cacheKey ->
				RegexBasedEvictionCache.from(cacheKey, getCacheKeysFunction(), getCacheEvictionFunction()));
		}
	}

	/**
	 * Spring {@link Cache} implementation that uses a Regular Expression (REGEX) {@link Pattern}
	 * to match cache entries by key for eviction.
	 *
	 * @see io.stackoverflow.questions.answers.spring.cache.AbstractCacheDelegate
	 * @see org.springframework.cache.Cache
	 */
	static class RegexBasedEvictionCache extends AbstractCacheDelegate {

		static @NonNull RegexBasedEvictionCache from(@NonNull Cache cache,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction,
				@Nullable BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction) {

			return new RegexBasedEvictionCache(cache, cacheKeysFunction, cacheEvictionFunction);
		}

		// Default Cache eviction algorithm.
		protected static final BiFunction<Cache, List<Object>, Boolean> DEFAULT_CACHE_EVICTION_FUNCTION =
			(cache, keys) -> {
				keys.forEach(cache::evict);
				return true;
			};

		// BiFunction that accepts a Cache and List of Keys to evict, then returns a boolean value
		// indicating whether the eviction was a success.
		@Getter(AccessLevel.PROTECTED)
		private final BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction;

		@Getter(AccessLevel.PROTECTED)
		private final RegexCacheKeysResolver cacheKeysResolver;

		RegexBasedEvictionCache(@NonNull Cache cache,
				@Nullable BiFunction<Cache, Object, List<Object>> cacheKeysFunction,
				@Nullable BiFunction<Cache, List<Object>, Boolean> cacheEvictionFunction) {

			super(cache);

			this.cacheKeysResolver = RegexCacheKeysResolver.from(cacheKeysFunction);

			this.cacheEvictionFunction = cacheEvictionFunction != null
				? cacheEvictionFunction
				: DEFAULT_CACHE_EVICTION_FUNCTION;
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

			Cache cache = getCache();

			List<Object> keys = getCacheKeysResolver().resolve(cache, key);

			getCacheEvictionFunction().apply(cache, keys);
		}
	}

	/**
	 * Strategy interface used to resolve a {@link List} all {@link Object keys} from the given {@link Cache}
	 * matching the given {@link Object key} {@link Pattern} (or prefix, namespace, etc).
	 *
	 * @see java.lang.FunctionalInterface
	 * @see <a href="https://en.wikipedia.org/wiki/Strategy_pattern">Strategy Software Design Pattern</a>
	 */
	@FunctionalInterface
	interface CacheKeysResolver {
		List<Object> resolve(Cache cache, Object key);
	}

	static class RegexCacheKeysResolver implements CacheKeysResolver {

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

		// Many underlying Cache implementations implement the java.util.Map interface (handle accordingly).
		// For Example: Apache Geode cache Region: https://geode.apache.org/releases/latest/javadoc/org/apache/geode/cache/Region.html
		protected static final BiFunction<Cache, Object, List<Object>> DEFAULT_CACHE_KEYS_FUNCTION =
			(cache, key) -> {

				Object nativeCache = cache.getNativeCache();

				return nativeCache instanceof Map
					? new ArrayList<>(((Map<?, ?>) nativeCache).keySet())
					: Collections.singletonList(key);
			};

		// BiFunction that accepts a Cache, the original Key and returns a List of Keys based on REGEX Pattern matching.
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
				: DEFAULT_CACHE_KEYS_FUNCTION;
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
		@Override
		public List<Object> resolve(@NonNull Cache cache, @NonNull Object key) {

			try {

				// If and only if the Regular Expression (REGEX) Pattern compiles should we go on to get all keys
				// from the Cache, which, depending on the cache implementation, could be resource expensive
				// (e.g. memory, network, etc).
				Pattern keyPattern = Pattern.compile(String.valueOf(key));

				// The Pattern compiled so now go get all the available keys in the cache; i.e. all keys
				// that are mapped to a value, stored in the cache.
				List<Object> allKeys = getCacheKeysFunction().apply(cache, key);

				// Filter all Cache keys based on its String representation matching the Regular Expression
				// (REGEX) Pattern and return only those Cache keys that match.
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
