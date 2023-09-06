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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Integration Tests testing whether the Spring Framework's Cache Abstraction can combine {@link Cacheable}
 * and {@link CachePut} on the same service method.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.junit.jupiter.api.extension.ExtendWith
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CachePut
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit.jupiter.SpringExtension
 * @see <a href="https://stackoverflow.com/questions/75795480/spring-caching-how-to-put-result-of-cachable-into-multiple-caches"></a>
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@ActiveProfiles("cacheable-cache-put")
@SuppressWarnings("unused")
public class CacheableAndCachePutAnnotatedServiceMethodsIntegrationTests {

	private static void assertCustomer(Customer customer, String name, Integer id, String accountNumber) {

		assertThat(customer).isNotNull();
		assertThat(customer.getName()).isEqualTo(name);
		assertThat(customer.getCustomerId()).isEqualTo(id);
		assertThat(customer.getAccountNumber()).isEqualTo(accountNumber);
	}

	private static void assertCustomer(Customer actual, Customer expected) {

		assertThat(actual).isNotSameAs(expected);
		assertCustomer(actual, expected.getName(), expected.getCustomerId(), expected.getAccountNumber());
	}

	@Autowired
	private CacheManager cacheManager;

	@AfterEach
	public void tearDown() {

		Set<String> namedCachesToClear = new HashSet<>(Arrays.asList("CacheOne", "CacheTwo"));

		this.cacheManager.getCacheNames().stream()
			.filter(namedCachesToClear::contains)
			.map(cacheName -> this.cacheManager.getCache(cacheName))
			.filter(Objects::nonNull)
			.forEach(Cache::clear);
	}

	@Autowired
	private CustomerService customerService;

	@Test
	public void cacheCustomerCorrectly() {

		assertThat(this.customerService.getRemoteService()).isNotNull();
		assertThat(this.customerService.isCacheMiss()).isFalse();

		Customer loadedJonDoe = this.customerService.findByCustomerId(12345);

		assertThat(this.customerService.isCacheMiss()).isTrue();
		assertThat(this.customerService.getRemoteService().getCallCount()).isOne();
		assertCustomer(loadedJonDoe, "Jon Doe", 12345, "abc123");
		assertThat(this.customerService.isCacheMiss()).isFalse();

		/*
		Customer cachedJonDoeByAccountNumber = this.customerService.findByAccountNumber("abc123");

		assertThat(this.customerService.isCacheMiss()).isFalse();
		assertCustomer(cachedJonDoeByAccountNumber, loadedJonDoe);
		*/

		Customer cachedJonDoeByCustomerId = this.customerService.findByCustomerId(12345);

		assertThat(this.customerService.isCacheMiss()).isFalse();
		assertThat(this.customerService.getRemoteService().getCallCount()).isOne();
		assertCustomer(cachedJonDoeByCustomerId, loadedJonDoe);
	}

	@Configuration
	@EnableCaching
	@Profile("cacheable-cache-put")
	static class TestConfiguration {

		private final static String[] cacheNames = { "customerCacheByCustomerId", "customerCacheByAccountNumber" };

		@Bean
		public CacheManager cacheManager() {

			ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(cacheNames);

			cacheManager.setAllowNullValues(false);
			cacheManager.setStoreByValue(true);

			return cacheManager;
		}

		@Bean
		RemoteService remoteService() {
			return new RemoteService();
		}

		@Bean
		CustomerService customerService(RemoteService remoteService) {
			return new CustomerService(remoteService);
		}
	}

	@Getter
	@Setter(AccessLevel.PACKAGE)
	@EqualsAndHashCode
	@ToString(of = "name")
	@RequiredArgsConstructor(staticName = "as")
	static class Customer implements Serializable {

		@Serial
		private static final long serialVersionUID = 5274900942602001275L;

		private Integer customerId;

		private String accountNumber;

		private final String name;

		@NonNull Customer identifiedBy(Integer id) {
			setCustomerId(id);
			return this;
		}

		@NonNull Customer withAccountNumber(String accountNumber) {
			setAccountNumber(accountNumber);
			return this;
		}
	}

	@Service
	public static class CustomerService {

		private final AtomicBoolean cacheMiss = new AtomicBoolean(false);

		private final RemoteService remoteService;

		public CustomerService(@NonNull RemoteService remoteService) {
			Assert.notNull(remoteService, "RemoveService is required");
			this.remoteService = remoteService;
		}

		protected @NonNull RemoteService getRemoteService() {
			return this.remoteService;
		}

		public boolean isCacheMiss() {
			return this.cacheMiss.getAndSet(false);
		}

		@Caching(
			cacheable = @Cacheable(cacheNames = "customerCacheByAccountNumber")
			//put =  @CachePut(cacheNames = "customerCacheByCustomerId", key = "#result.customerId")
		)
		public Customer findByAccountNumber(String accountNumber) {
			this.cacheMiss.set(true);
			return getRemoteService().findCustomer(accountNumber);
		}

		@Caching(
			cacheable = @Cacheable(cacheNames = "customerCacheByCustomerId")
			//put = @CachePut(cacheNames = "customerCacheByAccountNumber", key = "#result.accountNumber")
		)
		public Customer findByCustomerId(Integer id) {
			this.cacheMiss.set(true);
			return getRemoteService().findCustomer(id);
		}
	}

	@Service
	public static class RemoteService {

		private final AtomicInteger callCounter = new AtomicInteger(0);

		public int getCallCount() {
			return callCounter.get();
		}

		@NonNull Customer findCustomer(Object target) {
			this.callCounter.incrementAndGet();
			return Customer.as("Jon Doe")
				.identifiedBy(12345)
				.withAccountNumber("abc123");
		}
	}
}
