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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Integration Tests asserting and demonstration how to respond to {@link Cache} {@literal Eviction} Errors at runtime.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.mockito.Mockito
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.CacheEvict
 * @see org.springframework.cache.annotation.CachingConfigurer
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.interceptor.CacheErrorHandler
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.test.context.junit.jupiter.SpringExtension
 * @see <a href="https://stackoverflow.com/questions/71758914/spring-cache-error-handler-send-a-response-back-on-cacheevict-error">Spring Cache Error Handler - Send a Response back on CacheEvict Error</a>
 * @since 1.0.0
 */
@Getter(AccessLevel.PACKAGE)
@ExtendWith(SpringExtension.class)
@SuppressWarnings("unused")
public class RespondingToFailedCacheEvictionIntegrationTests {

	@Autowired
	private TestService testService;

	@Test
	public void cacheEvictionErrorReturnsTheCorrectResponse() {

		Response response = getTestService().clearAllCache();

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNull();
		assertThat(response.getStatus()).isEqualTo(Response.Status.ERROR);

		Throwable cacheException = response.getCause();

		assertThat(cacheException).isInstanceOf(CacheException.class);
		assertThat(cacheException).hasMessage("TEST");
		assertThat(cacheException).hasNoCause();
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration implements CachingConfigurer {

		@Bean
		TestService testService() {
			return new TestService();
		}

		@Override
		public CacheManager cacheManager() {

			ConcurrentMapCacheManager cacheManager = spy(new ConcurrentMapCacheManager());

			doAnswer(invocation -> {

				Cache cache = spy((Cache) invocation.callRealMethod());

				CacheException exception = new CacheException("TEST");

				doThrow(exception).when(cache).clear();
				doThrow(exception).when(cache).evict(any());
				doThrow(exception).when(cache).evictIfPresent(any());
				doThrow(exception).when(cache).invalidate();

				return cache;

			}).when(cacheManager).getCache(anyString());

			return cacheManager;
		}

		@Override
		public CacheErrorHandler errorHandler() {
			return new TestCacheErrorHandler();
		}
	}

	@Service
	static class TestService {

		//@CacheEvict(cacheNames = "TestCache", allEntries = true) // Causes Test to Fail!
		@CacheEvict(cacheNames = "TestCache", allEntries = true, beforeInvocation = true)
		public Response clearAllCache() {

			return new Response()
				.with("SUCCESS")
				.usingStatus(Response.Status.OK)
				.havingCause(TestCacheErrorHandler.getCacheError());
		}
	}

	@Component
	static class TestCacheErrorHandler extends SimpleCacheErrorHandler {

		private static final ThreadLocal<RuntimeException> cacheException = new ThreadLocal<>();

		public static @Nullable RuntimeException getCacheError() {

			RuntimeException resolvedCacheException = cacheException.get();

			cacheException.remove();

			return resolvedCacheException;
		}

		private final Logger logger = LoggerFactory.getLogger(getClass());

 		@Override
		public void handleCacheClearError(@NonNull RuntimeException exception, @NonNull Cache cache) {

			if (exception instanceof CacheException) {
				handleCacheException((CacheException) exception, cache, null);
			}
			else {
				super.handleCacheClearError(exception, cache);
			}
		}

		@Override
		public void handleCacheEvictError(@NonNull RuntimeException exception,
				@NonNull Cache cache, @NonNull Object key) {

			if (exception instanceof CacheException) {
				handleCacheException((CacheException) exception, cache, key);
			}
			else {
				super.handleCacheEvictError(exception, cache, key);
			}
		}

		private void handleCacheException(@NonNull CacheException exception,
				@NonNull Cache cache, @Nullable Object key) {

			this.logger.error("RuntimeException thrown during Cache [{}] Eviction: ", cache, exception);
			cacheException.set(exception);
		}
	}

	static class CacheException extends RuntimeException {

		public CacheException() { }

		public CacheException(String message) {
			super(message);
		}

		public CacheException(Throwable cause) {
			super(cause);
		}

		public CacheException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	@Getter
	@EqualsAndHashCode
	@ToString(of = "status")
	static class Response {

		private Object result;

		private Status status = Status.OK;

		private Throwable cause;

		public boolean isOk() {
			return Status.OK.equals(getStatus());
		}

		public Status getStatus() {
			Status resolvedStatus = this.status;
			resolvedStatus = resolvedStatus != null ? resolvedStatus : Status.OK;
			return resolvedStatus;
		}

		public Response with(Object result) {
			this.result = result;
			return this;
		}

		public Response usingStatus(Status status) {
			this.status = status;
			return this;
		}

		public Response havingCause(Throwable cause) {

			if (cause != null) {
				this.cause = cause;
				return with(null).usingStatus(Status.ERROR);
			}

			return this;
		}

		enum Status {
			OK, ERROR
		}
	}
}
