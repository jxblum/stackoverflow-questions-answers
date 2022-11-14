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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.shiro.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Integration Tests testing a query for application domain objects partially resolvable in the cache
 * and the remaining only resolvable in the database.
 *
 * @author John Blum
 * @see org.junit.jupiter.api.Test
 * @see org.springframework.boot.test.context.SpringBootTest
 * @see <a href="https://stackoverflow.com/questions/74411801/can-we-first-validate-the-existing-data-in-the-cache-then-go-for-the-missing-key">Can we first validate the existing data in the cache then go for the missing keys in Spring boot Cache?</a>
 * @since 1.0.0
 */
@SpringBootTest
@ContextConfiguration
@SuppressWarnings("unused")
public class PartialCachePartialDatabaseQueryResultsIntegrationTests {

	@Autowired
	@Getter(AccessLevel.PRIVATE)
	private StudentRepository studentRepository;

	@BeforeEach
	public void cacheSomeDoe() {
		getStudentRepository().save(Student.as("Jane Doe").identifiedBy(2));
		getStudentRepository().save(Student.as("Cookie Doe").identifiedBy(4));
		getStudentRepository().save(Student.as("Lan Doe").identifiedBy(6));
	}

	@Test
	public void resultSetIsCorrect() {

		getStudentRepository().setStudentIdAssertion(ids -> assertThat(ids).containsExactlyInAnyOrder(2, 4, 6));

		assertThat(getStudentRepository().findByIds(Set.of(2, 4, 6)))
			.containsExactlyInAnyOrder(
				Student.as("Jane Doe").identifiedBy(2),
				Student.as("Cookie Doe").identifiedBy(4),
				Student.as("Lan Doe").identifiedBy(6)
			);

		getStudentRepository().setStudentIdAssertion(ids -> assertThat(ids).containsExactlyInAnyOrder(3, 5));

		assertThat(getStudentRepository().findByIds(Set.of(2, 3, 4, 5, 6)))
			.containsExactlyInAnyOrder(
				Student.as("Jane Doe").identifiedBy(2),
				Student.as("Bob Doe").identifiedBy(3),
				Student.as("Cookie Doe").identifiedBy(4),
				Student.as("Fro Doe").identifiedBy(5),
				Student.as("Lan Doe").identifiedBy(6)
			);

		getStudentRepository().setStudentIdAssertion(ids -> assertThat(ids).isEmpty());

		assertThat(getStudentRepository().findByIds(Set.of(2, 3, 4, 5, 6)))
			.containsExactlyInAnyOrder(
				Student.as("Jane Doe").identifiedBy(2),
				Student.as("Bob Doe").identifiedBy(3),
				Student.as("Cookie Doe").identifiedBy(4),
				Student.as("Fro Doe").identifiedBy(5),
				Student.as("Lan Doe").identifiedBy(6)
			);
	}

	@SpringBootConfiguration
	@EnableCaching
	static class TestConfiguration {

		@Bean
		CacheManager cacheManager() {
			return new ConcurrentMapCacheManager("Students");
		}

		@Bean
		Cache studentCache(CacheManager cacheManager) {
			return cacheManager.getCache("Students");
		}

		@Bean
		Database studentDatabase() {
			return new Database();
		}

		@Bean
		StudentRepository studentRepository(@Qualifier("studentCache") Cache studentCache,
				@Qualifier("studentDatabase") Database studentDatabase) {

			return new StudentRepository(studentCache, studentDatabase);
		}
	}

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "as")
	static class Student implements Comparable<Student> {

		@Setter(AccessLevel.PROTECTED)
		private Integer id;

		private final String name;

		@Override
		public int compareTo(Student that) {
			return this.getName().compareTo(that.getName());
		}

		Student identifiedBy(Integer id) {
			setId(id);
			return this;
		}
	}

	@RequiredArgsConstructor
	static class StudentRepository {

		@Getter(AccessLevel.PRIVATE)
		private final Cache cache;

		@Getter(AccessLevel.PRIVATE)
		@Setter(AccessLevel.PUBLIC)
		private Consumer<Set<Integer>> studentIdAssertion = ids -> {};

		@Getter(AccessLevel.PRIVATE)
		private final Database database;

		//@Cacheable("Students")
		List<Student> findByIds(Set<Integer> studentIds) {

			List<Student> result = new ArrayList<>();

			studentIds.stream()
				.map(id -> this.getCache().get(id))
				.filter(Objects::nonNull)
				.map(Cache.ValueWrapper::get)
				.filter(Student.class::isInstance)
				.map(Student.class::cast)
				.forEach(result::add);

			Set<Integer> idsToQuery = new HashSet<>(studentIds);

			idsToQuery.removeAll(result.stream()
				.map(Student::getId)
				.collect(Collectors.toSet()));

			// Assert Student IDs that need to be queried and loaded from the database
			// vs. Student IDs present in the cache
			getStudentIdAssertion().accept(idsToQuery);

			getDatabase().query(idsToQuery).stream()
				.map(this::cache)
				.forEach(result::add);

			return result;
		}

		private Student cache(Student student) {
			getCache().put(student.getId(), student);
			return student;
		}

		@CachePut(cacheNames = "Students", key = "#student.id")
		Student save(Student student) {
			return getDatabase().save(student);
		}
	}

	static class Database {

		private static final Set<Student> students = new HashSet<>();

		static {
			students.addAll(Arrays.asList(
				Student.as("Jon Doe").identifiedBy(1),
				Student.as("Jane Doe").identifiedBy(2),
				Student.as("Bob Doe").identifiedBy(3),
				Student.as("Cookie Doe").identifiedBy(4),
				Student.as("Fro Doe").identifiedBy(5),
				Student.as("Lan Doe").identifiedBy(6),
				Student.as("Pie Doe").identifiedBy(7),
				Student.as("Sour Doe").identifiedBy(8)
			));
		}

		List<Student> query(Set<Integer> ids) {

			return students.stream()
				.filter(student -> ids.contains(student.getId()))
				.collect(Collectors.toList());
		}

		Student save(Student student) {
			Assert.notNull(student, "The Student to save is required");
			students.add(student);
			return student;
		}
	}
}
