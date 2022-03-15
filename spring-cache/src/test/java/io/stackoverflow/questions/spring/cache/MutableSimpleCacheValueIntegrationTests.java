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

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.geode.boot.autoconfigure.ClientCacheAutoConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.RequiredArgsConstructor;

/**
 * Integration Tests asserting that for a {@literal simple} {@link Cache} implementation, the {@link Cache} value
 * is mutable and other beans using the same {@link Cache} will see the result.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.cache.Cache
 * @see <a href="https://stackoverflow.com/questions/71451576/spring-cache-updates-when-read-value-updated-in-method">Spring cache updates when read value updated in method</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
	properties = "spring.cache.type=simple",
	webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@SuppressWarnings("unused")
public class MutableSimpleCacheValueIntegrationTests {

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private LockService lockService;

	@Autowired
	private LockCombinationChangeService lockCombinationChangeService;

	@Autowired
	private LockCombinationReaderService lockCombinationReaderService;

	@Before
	@SuppressWarnings("all")
	public void assertCacheConfiguration() {

		assertThat(this.cacheManager).isNotNull();

		Cache cache = this.cacheManager.getCache("TestCache");

		assertThat(cache).isInstanceOf(ConcurrentMapCache.class);
		assertThat(cache.getName()).isEqualTo("TestCache");
		assertThat(((ConcurrentMapCache) cache).isStoreByValue()).isFalse();
	}

	@Test
	public void cacheValueIsMutable() {

		assertThat(this.lockService.getCombination()).containsExactly(1, 1, 2, 4, 8);

		assertThat(this.lockService.isCacheMiss()).isTrue();

		this.lockCombinationChangeService.changeLockCombination(1, 1, 2, 3, 5);

		assertThat(this.lockService.isCacheMiss()).isFalse();

		this.lockCombinationReaderService.readLockCombination();

		assertThat(this.lockService.isCacheMiss()).isFalse();
	}

	@Configuration
	@EnableCaching
	@EnableAutoConfiguration(exclude = ClientCacheAutoConfiguration.class)
	static class TestConfiguration {

		@Bean
		public LockService lockService() {
			return new LockService();
		}

		@Bean
		public LockCombinationChangeService lockCombinationChangeService(LockService numberService) {
			return new LockCombinationChangeService(numberService);
		}

		@Bean
		public LockCombinationReaderService lockCombinationReaderService(LockService numberService) {
			return new LockCombinationReaderService(numberService);
		}
	}

	static class LockService extends CacheableService {

		@Cacheable("TestCache")
		public List<Integer> getCombination() {
			setCacheMiss();
			return Arrays.asList(1, 1, 2, 4, 8);
		}
	}

	@RequiredArgsConstructor
	static class LockCombinationChangeService {

		@lombok.NonNull
		private final LockService lockService;

		void changeLockCombination(int... newLockCombination) {

			List<Integer> currentLockCombination = this.lockService.getCombination();

			int currentLockCombinationSize = currentLockCombination.size();

			assertThat(newLockCombination).hasSize(currentLockCombinationSize);
			assertThat(currentLockCombination).containsExactly(1, 1, 2, 4, 8);

			for (int index = 0; index < currentLockCombinationSize; index++) {
				currentLockCombination.set(index, newLockCombination[index]);
			}
		}
	}

	@RequiredArgsConstructor
	static class LockCombinationReaderService {

		@lombok.NonNull
		private final LockService lockService;

		void readLockCombination() {
			assertThat(this.lockService.getCombination()).containsExactly(1, 1, 2, 3, 5);
		}
	}
}
