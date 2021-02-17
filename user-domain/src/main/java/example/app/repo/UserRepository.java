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
package example.app.repo;

import org.springframework.data.repository.CrudRepository;

import example.app.model.User;

/**
 * Spring Data {@link CrudRepository} and Data Access Object used to perform basic CRUD
 * and simple query data access operations on {@link User Users}.
 *
 * @author John Blum
 * @see org.springframework.data.repository.CrudRepository
 * @see example.app.model.User
 * @since 1.0.0
 */
public interface UserRepository extends CrudRepository<User, Integer> {

}
