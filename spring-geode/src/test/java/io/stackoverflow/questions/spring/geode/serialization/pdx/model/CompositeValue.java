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
package io.stackoverflow.questions.spring.geode.serialization.pdx.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.gemfire.mapping.annotation.Region;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * The {@link CompositeValue} class is an Abstract Data Type (ADT) and entity class encapsulating multiple values
 * as a single object.
 *
 * @author John Blum
 * @see org.springframework.data.annotation.Id
 * @see org.springframework.data.annotation.PersistenceConstructor
 * @see org.springframework.data.gemfire.mapping.annotation.Region
 * @since 1.0.0
 */
@Getter
@Region("Values")
public class CompositeValue {

	@Id
	private final Long valueOne;

	private final String valueTwo;

	@Setter
	private volatile Object valueThree;

	/*
	public CompositeValue() {
		this(0L, "nil");
	}
	*/

	public CompositeValue(@Nullable Object valueThree) {
		this(0L, "nil");
		this.valueThree = valueThree;
	}

	@PersistenceConstructor
	public CompositeValue(@NonNull Long valueOne, @NonNull String valueTwo) {

		Assert.notNull(valueOne, "Value one is required");
		Assert.hasText(valueTwo, "Value two is required");

		this.valueOne = valueOne;
		this.valueTwo = valueTwo;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof CompositeValue)) {
			return false;
		}

		CompositeValue that = (CompositeValue) obj;

		return ObjectUtils.nullSafeEquals(this.getValueOne(), that.getValueOne())
			&& ObjectUtils.nullSafeEquals(this.getValueTwo(), that.getValueTwo());
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public int hashCode() {

		int hashValue = 17;

		hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(this.getValueOne());
		hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(this.getValueTwo());

		return hashValue;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public String toString() {
		return String.format("{ valueOne = %1$d, valueTwo = '%2$s', valueThree = '%3$s' }",
			getValueOne(), getValueTwo(), getValueThree());
	}
}
