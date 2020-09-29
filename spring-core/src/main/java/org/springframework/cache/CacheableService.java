package org.springframework.cache;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for implementing cacheable services.
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public abstract class CacheableService {

	private final AtomicBoolean cacheMiss = new AtomicBoolean(false);

	public boolean isCacheHit() {
		return !isCacheMiss();
	}

	public boolean isCacheMiss() {
		return this.cacheMiss.compareAndSet(true, false);
	}

	protected void setCacheMiss() {
		this.cacheMiss.set(true);
	}
}
