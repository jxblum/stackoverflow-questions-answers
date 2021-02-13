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
package io.stackoverflow.questions.spring.geode.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.query.Struct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions;
import org.springframework.data.gemfire.repository.Query;
import org.springframework.data.gemfire.repository.config.EnableGemfireRepositories;
import org.springframework.data.gemfire.repository.query.annotation.Trace;
import org.springframework.data.gemfire.util.CollectionUtils;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;

import io.stackoverflow.questions.spring.geode.app.model.User;
import lombok.NonNull;

/**
 * Integration Tests asserting an Apache Geode OQL query returning a result set
 * for a count of some {@link Object} value equal to one.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.apache.geode.cache.query.Struct
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions
 * @see org.springframework.data.gemfire.repository.Query
 * @see org.springframework.data.gemfire.repository.config.EnableGemfireRepositories
 * @see org.springframework.data.repository.CrudRepository
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see <a href="https://stackoverflow.com/questions/66148859/gemfire-oql-query-how-do-i-use-count-of-a-select-statement-in-the-where-condit">Gemfire OQL Query - How do I use count of a SELECT statement in the Where Condition?</a>
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class CountQueryIntegrationTests {

	private static final AtomicBoolean initialize = new AtomicBoolean(false);

	@Autowired
	private UserRepository userRepository;

	private @NonNull Struct findById(@NonNull List<Struct> structs, @NonNull Long id) {

		return CollectionUtils.nullSafeList(structs).stream()
			.filter(Objects::nonNull)
			.filter(struct -> ObjectUtils.nullSafeEquals(struct.get("id"), id))
			.findFirst()
			.orElseThrow(() -> newIllegalStateException("Failed to find Struct with ID [%1$s] in List %2$s",
				id, structs));
	}

	private @NonNull Set<Long> findIds(@NonNull List<Struct> structs, @NonNull Predicate<Struct> idFilter) {

		return CollectionUtils.nullSafeList(structs).stream()
			.filter(Objects::nonNull)
			.filter(idFilter)
			.map(struct -> struct.get("id"))
			.map(String::valueOf)
			.map(Long::parseLong)
			.collect(Collectors.toSet());
	}

	private @NonNull Set<Long> findDuplicateIds(List<Struct> structs) {
		return findIds(structs, struct -> Integer.parseInt(String.valueOf(struct.get("cnt"))) > 1);
	}

	private @NonNull Set<Long> findUniqueIds(List<Struct> structs) {
		return findIds(structs, struct -> Integer.parseInt(String.valueOf(struct.get("cnt"))) == 1);
	}

	@Before
	public void setup() {

		assertThat(this.userRepository).isNotNull();

		if (initialize.compareAndSet(false, true)) {

			assertThat(this.userRepository.count()).isZero();

			this.userRepository.save(User.as("jonDoe").identifiedBy(1L));
			this.userRepository.save(User.as("janeDoe").identifiedBy(2L));
			this.userRepository.save(User.as("cookieDoe").identifiedBy(2L));
			this.userRepository.save(User.as("pieDoe").identifiedBy(2L));
			this.userRepository.save(User.as("sourDoe").identifiedBy(3L));

		}

		assertThat(this.userRepository.count()).isEqualTo(5L);
	}

	@Test
	public void countingUsersByIdIsCorrect() {

		List<Struct> structs = this.userRepository.countUsersById();

		assertThat(structs).isNotNull();
		assertThat(structs).hasSize(3);

		System.err.printf("[%s]%n", structs);

		assertThat(findById(structs, 1L).get("cnt")).isEqualTo(1);
		assertThat(findById(structs, 2L).get("cnt")).isEqualTo(3);
		assertThat(findById(structs, 3L).get("cnt")).isEqualTo(1);
	}

	@Test
	@Ignore
	public void duplicateCountQueryIsCorrect() {

		List<User> usersWithDuplicateId = this.userRepository.findUsersWithDuplicateId();

		assertThat(usersWithDuplicateId).isNotNull();
		assertThat(usersWithDuplicateId).hasSize(3);
		assertThat(usersWithDuplicateId).containsExactly(User.as("cookieDoe"), User.as("janeDoe"), User.as("pieDoe"));
	}

	@Test
	@Ignore
	public void uniqueCountQueryIsCorrect() {

		List<User> usersWithUniqueId = this.userRepository.findUsersWithUniqueId();

		assertThat(usersWithUniqueId).isNotNull();
		assertThat(usersWithUniqueId).hasSize(2);
		assertThat(usersWithUniqueId).containsExactly(User.as("jonDoe"), User.as("sourDoe"));
	}

	@Test
	public void findsUsersByDuplicateIds() {

		List<Struct> userCountById = this.userRepository.countUsersById();

		assertThat(userCountById).isNotNull();
		assertThat(userCountById).hasSize(3);

		Set<Long> duplicateUserIds = findDuplicateIds(userCountById);

		assertThat(duplicateUserIds).isNotNull();
		assertThat(duplicateUserIds).hasSize(1);
		assertThat(duplicateUserIds).containsExactlyInAnyOrder(2L);

		List<User> users = this.userRepository.findByIdInOrderByName(duplicateUserIds);

		assertThat(users).isNotNull();
		assertThat(users).hasSize(3);
		assertThat(users).containsExactly(User.as("cookieDoe"), User.as("janeDoe"), User.as("pieDoe"));
	}

	@Test
	public void findsUsersByUniqueIds() {

		List<Struct> userCountById = this.userRepository.countUsersById();

		assertThat(userCountById).isNotNull();
		assertThat(userCountById).hasSize(3);

		Set<Long> uniqueUserIds = findUniqueIds(userCountById);

		assertThat(uniqueUserIds).isNotNull();
		assertThat(uniqueUserIds).hasSize(2);
		assertThat(uniqueUserIds).containsExactlyInAnyOrder(1L, 3L);

		List<User> users = this.userRepository.findByIdInOrderByName(uniqueUserIds);

		assertThat(users).isNotNull();
		assertThat(users).hasSize(2);
		assertThat(users).containsExactly(User.as("jonDoe"), User.as("sourDoe"));
	}

	@ClientCacheApplication
	//@PeerCacheApplication
	//@EnablePdx
	@EnableEntityDefinedRegions(basePackageClasses = User.class,
		clientRegionShortcut = ClientRegionShortcut.LOCAL, serverRegionShortcut = RegionShortcut.PARTITION)
	@EnableGemfireRepositories(basePackageClasses = UserRepository.class)
	static class TestGeodeClientConfiguration { }

}

interface UserRepository extends CrudRepository<User, String> {

	@Trace
	@Query("SELECT u FROM /Users u WHERE u.id IN ($1) ORDER BY u.name ASC")
	List<User> findByIdInOrderByName(Collection<Long> ids);

	//@Query("SELECT x.id, count(*) AS cnt FROM /Users x WHERE count(*) = 1 GROUP BY x.id")
	//@Query("SELECT x.id FROM /Users x WHERE count(*) = 1 GROUP BY x.id")
	//@Query("SELECT x.id, count(*) AS cnt FROM /Users x WHERE cnt = 1 GROUP BY x.id")
	//@Query("SELECT x.id, count(*) AS cnt FROM /Users x WHERE cnt > 1 GROUP BY x.id")
	@Query("SELECT x.id, count(*) AS cnt FROM /Users x GROUP BY x.id")
	List<Struct> countUsersById();

	@Query("SELECT DISTINCT u FROM /Users u, (SELECT DISTINCT x.id AS id, count(*) AS cnt FROM /Users x GROUP BY x.id) v"
		+ " WHERE v.cnt > 1 AND u.id = v.id ORDER BY u.name ASC")
	List<User> findUsersWithDuplicateId();

	@Query("IMPORT io.stackoverflow.questions.spring.geode.app.model.UserIdCount;"
		+ " SELECT DISTINCT u FROM /Users u, (SELECT DISTINCT x.id AS id, count(*) AS cnt FROM /Users x GROUP BY x.id) v TYPE UserIdCount"
		+ " WHERE v.cnt = 1 AND u.id = v.id ORDER BY u.name ASC")
	//@Query("SELECT u FROM /Users u, (SELECT DISTINCT x.id AS id, count(*) AS cnt FROM /Users x GROUP BY x.id) v"
	//	+ " WHERE v.cnt = 1 AND u.id = v.id ORDER BY u.name ASC")
	List<User> findUsersWithUniqueId();

}
