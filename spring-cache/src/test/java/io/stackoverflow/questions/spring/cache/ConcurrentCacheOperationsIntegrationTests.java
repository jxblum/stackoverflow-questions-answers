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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests for Spring Cache Abstraction Synchronized Caching.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @link <a href="https://docs.spring.io/spring-framework/docs/5.3.8/reference/html/integration.html#cache-annotations-cacheable-synchronized">Synchronized Caching</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class ConcurrentCacheOperationsIntegrationTests {

	@Autowired
	private UserService userService;

	@Test
	public void concurrentCacheOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentCacheGetTestCase(this.userService));
	}

	public static class ConcurrentCacheGetTestCase extends MultithreadedTestCase {

		private final AtomicReference<User> userReference = new AtomicReference<>(null);

		@Getter
		private final UserService userService;

		public ConcurrentCacheGetTestCase(@NonNull UserService userService) {
			Assert.notNull(userService, "UserService is required");
			this.userService = userService;
		}

		@Override
		public void initialize() {

			super.initialize();

			doAnswer(invocation -> {

				User user = (User) invocation.callRealMethod();

				waitForTick(2);

				this.userReference.compareAndSet(null, user);

				return user;

			}).when(getUserService().getUserRepository()).findBy(anyString());
		}

		public void thread1() {

			Thread.currentThread().setName("Get User jonDoe 1");

			assertTick(0);

			User jonDoe = getUserService().findBy("jonDoe");

			assertThat(jonDoe).isNotNull();
			assertThat(jonDoe.getName()).isEqualTo("jonDoe");
		}

		public void thread2() {

			Thread.currentThread().setName("Get User jonDoe 2");

			waitForTick(1);

			User jonDoe = getUserService().findBy("jonDoe");

			assertThat(jonDoe).isNotNull();
			assertThat(jonDoe.getName()).isEqualTo("jonDoe");
			assertThat(jonDoe).isSameAs(userReference.get());
		}

		@Override
		public void finish() {

			UserRepository userRepository = getUserService().getUserRepository();

			verify(userRepository, times(1)).findBy(eq("jonDoe"));
			verifyNoMoreInteractions(userRepository);
		}
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		ConcurrentMapCacheManager cacheManager() {
			return new ConcurrentMapCacheManager("Users");
		}

		@Bean
		UserRepository userRepository() {

			return spy(new UserRepository() {

				private final AtomicInteger identifierSequence = new AtomicInteger(0);

				@Override
				public User findBy(String username) {
					return User.as(username).identifiedBy(identifierSequence.incrementAndGet());
				}
			});
		}

		@Bean
		UserService userService(UserRepository userRepository) {
			return new UserService(userRepository);
		}
	}

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "as")
	static class User {

		private Integer id;

		@lombok.NonNull
		private final String name;

		public User identifiedBy(@NonNull Integer id) {
			this.id = id;
			return this;
		}
	}

	@Repository
	interface UserRepository {
		User findBy(String username);
	}

	@Service
	static class UserService extends CacheableService {

		@Getter(AccessLevel.PROTECTED)
		private final UserRepository userRepository;

		public UserService(@NonNull UserRepository userRepository) {
			Assert.notNull(userRepository, "UserRepository is required");
			this.userRepository = userRepository;
		}

		@Cacheable(cacheNames = "Users", sync = true)
		public User findBy(String username) {
			setCacheMiss();
			return getUserRepository().findBy(username);
		}
	}
}
