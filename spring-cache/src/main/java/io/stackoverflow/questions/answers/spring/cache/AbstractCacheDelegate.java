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

import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import lombok.Getter;

/**
 * Abstract base class implementing the Spring {@link Cache} interface by decorating and delegating to
 * a backing {@link Cache} implementation.
 *
 * @author John Blum
 * @see org.springframework.cache.Cache
 * @since 1.0.0
 */
public class AbstractCacheDelegate implements Cache {

	@Getter
	private final Cache cache;

	public AbstractCacheDelegate(@NonNull Cache cache) {
		Assert.notNull(cache, "Cache to delegate to is required");
		this.cache = cache;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public @NonNull String getName() {
		return getCache().getName();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public @NonNull Object getNativeCache() {
		return getCache().getNativeCache();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public ValueWrapper get(@NonNull Object key) {
		return getCache().get(key);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public <T> T get(@NonNull Object key, Class<T> type) {
		return getCache().get(key, type);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
		return getCache().get(key, valueLoader);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void put(@NonNull Object key, Object value) {
		getCache().put(key, value);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void evict(@NonNull Object key) {
		getCache().evict(key);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void clear() {
		getCache().clear();
	}
}
