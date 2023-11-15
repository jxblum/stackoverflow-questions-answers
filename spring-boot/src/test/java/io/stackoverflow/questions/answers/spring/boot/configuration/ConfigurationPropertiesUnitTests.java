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
package io.stackoverflow.questions.answers.spring.boot.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit Tests for Spring Boot {@link ConfigurationProperties}.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.springframework.boot.SpringBootConfiguration
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see org.springframework.boot.context.properties.EnableConfigurationProperties
 * @see org.springframework.boot.test.context.SpringBootTest
 * @since 1.0.0
 */
@SpringBootTest(properties = {
	"example.app.test.name=JackBlack",
	"example.app.test.embedded.value=MOCK",
})
@SuppressWarnings("unused")
public class ConfigurationPropertiesUnitTests {

	@Autowired
	private TestProperties testProperties;

	@Test
	void testPropertiesAreCorrect() {

		assertThat(this.testProperties).isNotNull();
		assertThat(this.testProperties.getName()).isEqualTo("JackBlack");

		TestProperties.Embedded embedded = this.testProperties.getEmbedded();

		assertThat(embedded).isNotNull();
		assertThat(embedded.getValue()).isEqualTo("MOCK");
	}

	@SpringBootConfiguration
	@EnableConfigurationProperties(TestProperties.class)
	static class TestConfiguration {

	}

	@ConfigurationProperties("example.app.test")
	public static class TestProperties {

		private final Embedded embedded = new Embedded(this);

		private String name;

		public Embedded getEmbedded() {
			return this.embedded;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public static class Embedded {

			private String value;

			private final TestProperties properties;

			protected Embedded(TestProperties properties) {
				this.properties = properties;
			}

			public String getValue() {
				return this.value;
			}

			public void setValue(String value) {
				this.value = value;
			}
		}
	}
}
