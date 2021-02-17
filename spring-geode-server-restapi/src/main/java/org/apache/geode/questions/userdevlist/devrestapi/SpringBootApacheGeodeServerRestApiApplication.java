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
package org.apache.geode.questions.userdevlist.devrestapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.distributed.Locator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions;
import org.springframework.data.gemfire.config.annotation.EnableHttpService;
import org.springframework.data.gemfire.config.annotation.EnableLocator;
import org.springframework.data.gemfire.config.annotation.EnableManager;
import org.springframework.data.gemfire.repository.config.EnableGemfireRepositories;

import example.app.model.User;
import example.app.repo.UserRepository;

/**
 * {@link SpringBootApplication} used to bootstrap and configure an Apache Geode {@link CacheServerApplication}
 * with a {@link CacheServer}, {@link Locator}, {@literal Manager} and the Apache Geode Developer REST API enabled.
 *
 * @author John Blum
 * @see org.springframework.boot.ApplicationRunner
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see org.springframework.boot.builder.SpringApplicationBuilder
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.data.gemfire.config.annotation.CacheServerApplication
 * @see org.springframework.data.gemfire.config.annotation.EnableHttpService
 * @see org.springframework.data.gemfire.config.annotation.EnableLocator
 * @see org.springframework.data.gemfire.config.annotation.EnableManager
 * @since 1.0.0
 */
@SpringBootApplication
@SuppressWarnings("unused")
public class SpringBootApacheGeodeServerRestApiApplication {

	public static void main(String[] args) {

		configureGemFireHomeSystemProperty();

		new SpringApplicationBuilder(SpringBootApacheGeodeServerRestApiApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("dev-rest-api")
			.build()
			.run(args);
	}

	private static void configureGemFireHomeSystemProperty() {
		System.setProperty("gemfire.home", "/Users/jblum/pivdev/apache-geode-1.13.1");
	}

	@Bean
	ApplicationRunner run(UserRepository userRepository) {

		return args -> {

			userRepository.save(User.as("jonDoe").identifiedBy(1L));
			userRepository.save(User.as("janeDoe").identifiedBy(2L));

			assertThat(userRepository.count()).isEqualTo(2L);

			System.err.printf("%s is running!%n", getClass().getSimpleName());
		};
	}

	@CacheServerApplication
	@EnableLocator
	@EnableManager(start = true)
	@EnableEntityDefinedRegions(basePackageClasses = User.class)
	@EnableGemfireRepositories(basePackageClasses = UserRepository.class)
	static class CacheServerLocatorManagerConfiguration { }

	@Configuration
	@Profile("dev-rest-api")
	@EnableHttpService(startDeveloperRestApi = true)
	static class DeveloperRestApiConfiguration { }

}
