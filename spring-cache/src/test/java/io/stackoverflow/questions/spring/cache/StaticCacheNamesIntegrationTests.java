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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Testing the behavior of Spring Caching with static cache names configured.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.mockito.Mockito
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.cache.concurrent.ConcurrentMapCacheManager
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.test.context.junit.jupiter.SpringExtension
 * @see <a href="https://stackoverflow.com/questions/71756692/cacheable-testing-over-method">Cacheable Testing over Method</a>
 * @since 1.0.0
 */
@Getter
@ExtendWith(SpringExtension.class)
@SuppressWarnings("unused")
public class StaticCacheNamesIntegrationTests {

	@Autowired
	private LeadRepository leadRepository;

	@Autowired
	private LeadService leadService;

	@AfterEach
	void verfiyAfterTest() {
		verify(getLeadRepository(), times(1)).findById(any());
	}

	@Test
	public void cachingIsUsed() {

		Lead lead = Lead.identifiedBy("DC635EA19A39EA128764BB99052E5D1A9A");

		// Calls the AOP Proxy for the LeadService bean's @Cacheable load(:Lead) method.
		Lead loadedLead = getLeadService().load(lead);

		// Causes test to fail since the @Cacheable load(:Lead) method is not called outside
		// the AOP Proxy for the LeadService bean.
		//Lead loadedLead = getLeadService().process(lead);

		assertThat(loadedLead).isNotNull();
		assertThat(loadedLead).isNotSameAs(lead);
		assertThat(loadedLead).isEqualTo(lead);

		Lead cachedLead = getLeadService().load(lead);

		assertThat(cachedLead).isSameAs(loadedLead);
	}

	@Configuration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		CacheManager cacheManager() {
			//return new ConcurrentMapCacheManager("LEAD_DATA"); // INCORRECT CACHE NAME!
			return new ConcurrentMapCacheManager("leads");
		}

		@Bean
		LeadRepository leadRepository() {
			return spy(new LeadRepository());
		}

		@Bean
		LeadService leadService(LeadRepository leadRepository) {
			return new LeadService(leadRepository);
		}
	}

	@Service
	@Getter(AccessLevel.PROTECTED)
	@RequiredArgsConstructor
	static class LeadService {

		@lombok.NonNull
		private final LeadRepository leadRepository;

		@SuppressWarnings("all")
		@Cacheable(cacheNames = "leads", key = "#lead.id")
		public Lead load(Lead lead) {
			Lead loadedLead = getLeadRepository().findById(lead.getId());
			// map or transform the loaded Lead
			return loadedLead;
		}

		public Lead process(Lead lead) {
			return load(lead);
		}
	}

	@Repository
	static class LeadRepository {

		Lead findById(String id) {
			return Lead.identifiedBy(id);
		}
	}

	@Getter
	@ToString
	@EqualsAndHashCode(of = "id")
	@RequiredArgsConstructor(staticName = "identifiedBy")
	static class Lead {

		@lombok.NonNull
		private final String id;

	}
}
