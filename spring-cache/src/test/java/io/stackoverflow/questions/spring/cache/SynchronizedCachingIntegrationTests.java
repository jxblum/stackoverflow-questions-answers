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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests testing and asserting synchronized caching with Spring Cache Abstraction.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.boot.test.context.SpringBootTest
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.test.context.ActiveProfiles
 * @see org.springframework.test.context.junit.jupiter.SpringExtension
 * @since 0.1.0
 */
@ActiveProfiles("synchronized-caching")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@SuppressWarnings("unused")
public class SynchronizedCachingIntegrationTests {

	@Autowired
	private UserService userService;

	@Test
	public void synchronizedCachingIsCorrect() throws Throwable {
		TestFramework.runOnce(new SynchronizedCachingMultithreadedTestCase(this.userService));
	}

	@EnableCaching
	@SpringBootConfiguration
	@Profile("synchronized-caching")
	static class TestConfiguration {

		@Bean
		CacheManager cacheManager() {
			return new ConcurrentMapCacheManager("Users");
		}

		@Bean
		UserService userService() {
			return new UserService();
		}
	}

	static class SynchronizedCachingMultithreadedTestCase extends MultithreadedTestCase {

		private static final String TEST_USERNAME = "JonDoe";

		private final AtomicInteger counter = new AtomicInteger(0);

		@Getter(AccessLevel.PACKAGE)
		private final UserService userService;

		private SynchronizedCachingMultithreadedTestCase(@NonNull UserService userService) {
			Assert.notNull(userService, "UserService is required");
			this.userService = userService;
		}

		@SuppressWarnings("all")
		private Function<User, User> newUserProcessor(int tick) {

			return spy(new Function<User, User>() {

				@Override
				public User apply(User user) {
					assertThat(counter.incrementAndGet())
						.describedAs("Thread [%s] - expected [1]; but was [%d]",
							Thread.currentThread().getName(), counter.get())
						.isOne();
					waitForTick(2);
					assertThat(counter.decrementAndGet()).isZero();
					return user;
				}
			});
		}

		public void thread1() {

			assertTick(0);
			Thread.currentThread().setName("UserService Accessor Thread One");

			Function<User, User> userProcessor = newUserProcessor(2);

			User user = getUserService().findUserByName(TEST_USERNAME, userProcessor).orElse(null);

			assertThat(user).isNotNull();
			assertThat(user.getName()).isEqualTo(TEST_USERNAME);

			// Interaction on the userProcessor Function implies a cache miss
			verify(userProcessor, times(1)).apply(eq(user));
		}

		public void thread2() {

			assertTick(0);
			Thread.currentThread().setName("UserService Accessor Thread Two");

			waitForTick(1);

			Function<User, User> userProcessor = newUserProcessor(3);

			User user = getUserService().findUserByName("JaneDoe", userProcessor).orElse(null);

			assertThat(user).isNotNull();
			assertThat(user.getName()).isEqualTo("JaneDoe");

			// No interaction on the userProcessor Function implies a cache hit
			verifyNoInteractions(userProcessor);
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

		@Cacheable(cacheNames = "Users", key = "#username", sync = true)
		public Optional<User> findUserByName(@NonNull String username, @NonNull Function<User, User> processor) {
			return Optional.ofNullable(processor.apply(User.as(username)));
		}
	}
}
