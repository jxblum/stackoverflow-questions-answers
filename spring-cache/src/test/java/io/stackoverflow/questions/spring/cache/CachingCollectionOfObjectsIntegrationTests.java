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

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.stackoverflow.questions.answers.spring.cache.annotation.EnableSimpleCaching;

import org.junit.jupiter.api.Test;

import org.apache.shiro.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Integration Tests testing the Spring Cache Abstraction with a Cache for a {@link Collection} of objects.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.springframework.boot.test.context.SpringBootTest
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CachePut
 * @see <a href="https://stackoverflow.com/questions/77399702/unable-to-update-already-cached-list-using-cacheput">Unable to update already cached list using @CachePut</a>
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("cache-collection")
@SuppressWarnings("unused")
public class CachingCollectionOfObjectsIntegrationTests {

	@Autowired
	private CustomerService customerService;

	@Test
	void customerUpdateUpdatesListInCache() {

		LocalDate date = LocalDate.of(2023, Month.NOVEMBER, 11);

		List<Customer> customersOne = this.customerService.findByDate(date);

		assertThat(this.customerService.wasCacheMiss()).isTrue();
		assertCustomers(customersOne, "Jon Doe 1", "Jane Doe 1");
		assertCustomerWithRandomData(customersOne, "Jon Doe 1", "TEST");

		List<Customer> customersTwo = this.customerService.findByDate(LocalDate.now());

		assertThat(this.customerService.wasCacheMiss()).isTrue();
		assertCustomers(customersTwo, "Jon Doe 2", "Jane Doe 2");

		List<Customer> cachedCustomersOne = this.customerService.findByDate(date);

		assertThat(this.customerService.wasCacheMiss()).isFalse();
		assertThat(cachedCustomersOne).isEqualTo(customersOne);

		// ignore this List and reload from findByDate(:LocalDate)
		// TODO: call badUpdateCustomer(..) service method to see the ClassCastException occur
		this.customerService.updateCustomer(date, Customer.named("Jon Doe 1").withRandomData("MOCK"));

		List<Customer> updatedCachedCustomersOne = this.customerService.findByDate(date);

		assertThat(this.customerService.wasCacheMiss()).isFalse();
		assertThat(updatedCachedCustomersOne).isEqualTo(customersOne);
		assertCustomerWithRandomData(updatedCachedCustomersOne, "Jon Doe 1", "MOCK");
	}

	private void assertCustomers(List<Customer> customers, String... expectedCustomerNames) {

		assertThat(customers).isNotNull();
		assertThat(customers).hasSize(expectedCustomerNames.length);
		assertThat(customers.stream().map(Customer::getName).toList()).containsExactlyInAnyOrder(expectedCustomerNames);
	}

	private void assertCustomerWithRandomData(List<Customer> customers, String customerName, Object expectedData) {

		assertThat(customers.stream()
			.filter(customer -> customer.getName().equals(customerName))
			.findFirst()
			.map(Customer::getRandomData)
			.orElse(null))
			.isEqualTo(expectedData);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableSimpleCaching
	@Profile("cache-collection")
	static class TestConfiguration {

		static final String CUSTOMERS_CACHE_NAME = "CustomersCache";

		@Bean
		CustomerService customerService(CacheManager cacheManager) {
			return new CustomerService(cacheManager);
		}
	}

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	static class Customer {

		public static Customer named(String name) {
			Assert.hasText(name, "Customer name is required");
			return new Customer(name);
		}

		@Setter(AccessLevel.PROTECTED)
		private Object randomData;

		private final String name;

		public Customer withRandomData(Object randomData) {
			setRandomData(randomData);
			return this;
		}
	}

	@Service
	@Getter(AccessLevel.PROTECTED)
	static class CustomerService {

		private final AtomicBoolean cacheMiss = new AtomicBoolean(false);

		private final AtomicInteger counter = new AtomicInteger(0);

		private final Cache customers;

		CustomerService(CacheManager cacheManager) {

			Assert.notNull(cacheManager, "CacheManager is required");

			this.customers = assertNotNull(cacheManager.getCache(TestConfiguration.CUSTOMERS_CACHE_NAME),
				"CustomersCache is required");
		}

		private <T> T assertNotNull(T target, String message, Object... arguments) {
			Assert.notNull(target, message.formatted(arguments));
			return target;
		}

		@Cacheable(cacheNames = TestConfiguration.CUSTOMERS_CACHE_NAME)
		public List<Customer> findByDate(LocalDate date) {

			this.cacheMiss.set(true);

			int count = this.counter.incrementAndGet();

			// Most likely fetching Customers from an RDBMS or Web service (Microservice REST API call)...
			return new ArrayList<>(List.of(
				Customer.named("Jon Doe " + count).withRandomData("TEST"),
				Customer.named("Jane Doe " + count).withRandomData("TEST")
			));
		}

		@CachePut(cacheNames = TestConfiguration.CUSTOMERS_CACHE_NAME, key="#date")
		public List<Customer> updateCustomer(LocalDate date, Customer customer) {

			List<Customer> customers = resolveCustomerList(date);

			return saveToList(customer, customers);
		}

		// NOTE: This service method implementation will cause the ClassCastException; Don't do this!
		// It's not going to update the cached List as you might think; Spring's Cache Abstraction
		// does not work that way (OOTB).
		// Also, you could update the List as in the updateCustomer(..) service method, but it is useless.
		@CachePut(cacheNames = TestConfiguration.CUSTOMERS_CACHE_NAME, key = "#date")
		public Customer badUpdateCustomer(LocalDate date, Customer customer) {
			return customer;
		}

		// TODO: Use Case Dependent - Should a new List be created or an Exception thrown if a List of Customers
		//  was not previously cached by the given date!?
		@SuppressWarnings("unchecked")
		private List<Customer> resolveCustomerList(LocalDate date) {
			List<Customer> customers = getCustomers().get(date, List.class);
			return customers != null ? customers : new ArrayList<>();
		}

		// TODO: Use Case Dependent - Should the Customer really be added to the List, which means the List
		//  was not cached by the given key (date)!?
		private List<Customer> saveToList(Customer customer, List<Customer> customers) {

			int customerIndex = customers.indexOf(customer);

			if (customerIndex > -1) {
				customers.set(customerIndex, customer);
			}
			else {
				customers.add(customer);
			}

			return customers;
		}

		public boolean wasCacheMiss() {
			return this.cacheMiss.getAndSet(false);
		}
	}
}
