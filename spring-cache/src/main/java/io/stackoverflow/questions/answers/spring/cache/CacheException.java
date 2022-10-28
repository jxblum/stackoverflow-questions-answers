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
package io.stackoverflow.questions.answers.spring.cache;

import org.springframework.cache.Cache;

/**
 * Java {@link RuntimeException} used to classify all Spring {@link Cache} exceptions.
 *
 * @author John Blum
 * @see java.lang.RuntimeException
 * @see org.springframework.cache.Cache
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class CacheException extends RuntimeException {

	public CacheException() { }

	public CacheException(String message) {
		super(message);
	}

	public CacheException(Throwable cause) {
		super(cause);
	}

	public CacheException(String message, Throwable cause) {
		super(message, cause);
	}
}
