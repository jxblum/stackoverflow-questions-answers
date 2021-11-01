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
package io.stackoverflow.questions.spring.geode.serialization.pdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxSerializer;
import org.apache.geode.pdx.PdxWriter;
import org.apache.geode.pdx.ReflectionBasedAutoSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions;
import org.springframework.data.gemfire.config.annotation.EnablePdx;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.data.gemfire.repository.config.EnableGemfireRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.stackoverflow.questions.spring.geode.serialization.pdx.model.CompositeValue;
import io.stackoverflow.questions.spring.geode.serialization.pdx.repo.CompositeValueRepository;

/**
 * Integration Test testing and asserting the successful persistence of an ADT / entity type with a no-arg constructor
 * using Spring Data for Apache Geode (and VMware Tanzu (Pivotal) GemFire) features.
 *
 * @author John Blum
 * @see org.apache.geode.pdx.PdxSerializer
 * @see org.apache.geode.pdx.ReflectionBasedAutoSerializer
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Import
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.data.annotation.PersistenceConstructor
 * @see org.springframework.data.gemfire.config.annotation.EnablePdx
 * @see org.springframework.data.gemfire.config.annotation.PeerCacheApplication
 * @see org.springframework.test.context.ActiveProfiles
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see <a href="https://stackoverflow.com/questions/69797245/how-to-deserialize-com-auth0-jwk-jwk-that-does-not-have-no-arg-constructor">How to deserialize com.auth0.jwk.Jwk that does not have no-arg constructor?</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@ActiveProfiles("spring")
@SuppressWarnings("unused")
public class PdxSerializationOfEntityWithNonDefaultConstructor {

	@Autowired
	private CompositeValueRepository repository;

	@Test
	public void saveAndFindCompositeValueIsSuccessful() {

		CompositeValue compositeValue = new CompositeValue(1L, UUID.randomUUID().toString());

		compositeValue.setValueThree(BigDecimal.valueOf(123.45d));

		this.repository.save(compositeValue);

		CompositeValue loadedCompositeValue = this.repository.findById(compositeValue.getValueOne()).orElse(null);

		assertThat(loadedCompositeValue).isNotNull();
		assertThat(loadedCompositeValue).isNotSameAs(compositeValue);
		assertThat(loadedCompositeValue).isEqualTo(compositeValue);
		assertThat(loadedCompositeValue.getValueThree()).isEqualTo(compositeValue.getValueThree());
	}

	@PeerCacheApplication
	@EnableEntityDefinedRegions(basePackageClasses = CompositeValue.class,
		serverRegionShortcut = RegionShortcut.PARTITION)
	@EnableGemfireRepositories(basePackageClasses = CompositeValueRepository.class)
	@Import({ GemFireSerializationConfiguration.class, GemFireCustomPdxSerializationConfiguration.class, SpringSerializationConfiguration.class })
	static class TestConfiguration { }

	@Configuration
	@Profile("gemfire")
	@EnablePdx(serializerBeanName = "reflectionAutoSerializer")
	static class GemFireSerializationConfiguration {

		@Bean
		ReflectionBasedAutoSerializer reflectionAutoSerializer() {
			return new ReflectionBasedAutoSerializer(".*");
		}
	}

	@Configuration
	@Profile("gemfire-custom-pdxserializer")
	@EnablePdx(serializerBeanName = "customPdxSerializer")
	static class GemFireCustomPdxSerializationConfiguration {

		@Bean
		PdxSerializer customPdxSerializer() {

			return new PdxSerializer() {

				@Override
				public boolean toData(Object value, PdxWriter pdxWriter) {

					if (value instanceof CompositeValue) {

						CompositeValue compositeValue = (CompositeValue) value;

						pdxWriter.writeLong("valueOne", compositeValue.getValueOne());
						pdxWriter.writeString("valueTwo", compositeValue.getValueTwo());
						pdxWriter.writeObject("valueThree", compositeValue.getValueThree());

						return true;
					}

					return false;
				}

				@Override
				public Object fromData(Class<?> type, PdxReader pdxReader) {

					if (type != null && CompositeValue.class.isAssignableFrom(type)) {

						CompositeValue value =
							new CompositeValue(pdxReader.readLong("valueOne"), pdxReader.readString("valueTwo"));

						value.setValueThree(pdxReader.readObject("valueThree"));

						return value;
					}

					return null;
				}
			};
		}
	}

	@Configuration
	@Profile("spring")
	@EnablePdx
	static class SpringSerializationConfiguration { }

}
