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
package io.stackoverflow.questions.java.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.util.ObjectUtils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Integration Tests asserting the behavior of a Java {@link Map} (e.g. {@link HashMap})
 * when the {@link Object#hashCode()} of an {@link Object} stored in the {@link Map} changes.
 *
 * @author John Blum@
 * @see java.lang.Object
 * @see java.util.HashMap
 * @see java.util.Map
 * @since 1.0.0
 */
public class MapStoredObjectMutationIntegrationTests {

	@Test
	public void hashMapStoredObjectBehavior() {

		User user = User.as("jonDoe");

		Map<String, User> users = new HashMap<>();

		int userHashCode = user.hashCode();

		assertThat(user.getName()).isEqualTo("jonDoe");
		assertThat(users).isEmpty();
		assertThat(users.put(user.getName(), user)).isNull();
		assertThat(users).hasSize(1);
		assertThat(users.containsValue(user)).isTrue();
		assertThat(users.get(user.getName())).isEqualTo(user);

		user.setName("jackHandy");

		assertThat(user.getName()).isEqualTo("jackHandy");
		assertThat(user.hashCode()).isNotEqualTo(userHashCode);
		assertThat(users).hasSize(1);
		assertThat(users.containsValue(user)).isTrue();
		assertThat(users.get(user.getName())).isNull();
	}

	@Getter
	@RequiredArgsConstructor(staticName = "as")
	static class User {

		@lombok.NonNull
		@Setter(AccessLevel.PRIVATE)
		private String name;

		/**
		 * @inheritDoc
		 */
		@Override
		public boolean equals(Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof User)) {
				return false;
			}

			User that = (User) obj;

			return ObjectUtils.nullSafeEquals(this.getName(), that.getName());
		}

		/**
		 * @inheritDoc
		 */
		@Override
		public int hashCode() {

			int hashValue = 17;

			hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getName());

			return hashValue;
		}

		/**
		 * @inheritDoc
		 */
		@Override
		public String toString() {
			return getName();
		}
	}
}
