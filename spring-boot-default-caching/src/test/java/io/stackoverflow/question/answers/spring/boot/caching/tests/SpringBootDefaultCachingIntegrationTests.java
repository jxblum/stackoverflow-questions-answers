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
package io.stackoverflow.question.answers.spring.boot.caching.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests testing the default cache configuration of Spring Boot auto-configuration.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.boot.test.context.SpringBootTest
 * @see org.springframework.cache.annotation.EnableCaching
 * @see <a href="https://stackoverflow.com/questions/74766894/is-explicit-cachemanager-bean-definition-mandatory-when-using-spring-boot-spri">Is explicit CacheManager bean definition mandatory when using Spring Boot + Spring Cache?</a>
 * @since 1.0.0
 */
@SpringBootTest
@SuppressWarnings("unused")
public class SpringBootDefaultCachingIntegrationTests {

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private UserService userService;

	@BeforeEach
	public void cacheManagerIsConfiguredCorrectly() {

		assertThat(this.cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
		assertThat(this.cacheManager.getCache("Users")).isNotNull();
	}

	private void assertUser(User user, String expectedName) {

		assertThat(user).isNotNull();
		assertThat(user.getName()).isEqualTo(expectedName);
	}

	@Test
	public void userCachingWorksProperly() {

		User jonDoe = this.userService.findBy("JonDoe");

		assertUser(jonDoe, "JonDoe");
		assertThat(this.userService.isCacheMiss()).isTrue();

		User cachedJonDoe = this.userService.findBy("JonDoe");

		assertThat(cachedJonDoe).isSameAs(jonDoe);
		assertThat(this.userService.isCacheMiss()).isFalse();

		User janeDoe = this.userService.findBy("JaneDoe");

		assertUser(janeDoe, "JaneDoe");
		assertThat(this.userService.isCacheMiss()).isTrue();
	}

	@SpringBootConfiguration
	@AutoConfigureCache
	@EnableCaching
	static class TestConfiguration {

		@Bean
		UserService userService() {
			return new UserService();
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
	static class UserService extends CacheableService {

		@Cacheable("Users")
		public @NonNull User findBy(String name) {
			setCacheMiss();
			return User.as(name);
		}
	}
}
