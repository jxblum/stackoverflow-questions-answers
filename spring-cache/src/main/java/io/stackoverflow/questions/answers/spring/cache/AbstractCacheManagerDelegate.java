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

import java.util.Collection;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Abstract base class implementing the Spring {@link CacheManager} interface by decorating and delegating to
 * a backing {@link CacheManager} implementation.
 *
 * @author John Blum
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @since 1.0.0
 */
public abstract class AbstractCacheManagerDelegate implements CacheManager {

	@Getter(AccessLevel.PROTECTED)
	private final CacheManager cacheManager;

	public AbstractCacheManagerDelegate(@NonNull CacheManager cacheManager) {

		Assert.notNull(cacheManager, "The CacheManager to delegate operations to is required");

		this.cacheManager = cacheManager;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public @Nullable Cache getCache(@NonNull String name) {
		return getCacheManager().getCache(name);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public @NonNull Collection<String> getCacheNames() {
		return getCacheManager().getCacheNames();
	}
}
