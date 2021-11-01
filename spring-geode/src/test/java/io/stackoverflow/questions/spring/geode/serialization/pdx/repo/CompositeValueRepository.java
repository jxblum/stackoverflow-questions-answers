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
package io.stackoverflow.questions.spring.geode.serialization.pdx.repo;

import org.springframework.data.repository.CrudRepository;

import io.stackoverflow.questions.spring.geode.serialization.pdx.model.CompositeValue;

/**
 * Spring Data {@link CrudRepository} and Data Access Object (DAO) for performing basis {@literal CRUD}
 * and simple OQL query data access operations on {@link CompositeValue} objects.
 *
 * @author John Blum
 * @see org.springframework.data.repository.CrudRepository
 * @see io.stackoverflow.questions.spring.geode.serialization.pdx.model.CompositeValue
 * @since 1.0.0
 */
public interface CompositeValueRepository extends CrudRepository<CompositeValue, Long> {

}
