package org.springframework.data.repository;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;

/**
 * A Spring Data {@link CrudRepository} abstract base class simplifying the implementation of
 * Spring Data's {@link CrudRepository} interface by implementing most {@link CrudRepository}
 * basic CRUD and simply query data access operations in terms of the core {@link CrudRepository}
 * data access operations:
 *
 * <ul>
 *     <li><code>save(:S):S</code></li>
 *     <li><code>findAll():Iterable<T></code></li>
 *     <li><code>delete(:T)</code></li>
 * </ul>
 *
 * @author John Blum
 * @see org.springframework.data.repository.CrudRepository
 * @since 1.0.0
 */
public abstract class BaseCrudRepository<T, ID> implements CrudRepository<T, ID> {

	/**
	 * @inheritDoc
	 */
	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> iterable) {

		StreamSupport.stream(nullSafeIterable(iterable).spliterator(), false)
			.filter(Objects::nonNull)
			.forEach(this::save);

		return iterable;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public long count() {
		return StreamSupport.stream(nullSafeIterable(findAll()).spliterator(), false).count();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public boolean existsById(ID id) {
		return findById(id).isPresent();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public Iterable<T> findAllById(Iterable<ID> iterable) {

		Set<ID> identifiers = StreamSupport.stream(nullSafeIterable(iterable).spliterator(), false)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		return StreamSupport.stream(nullSafeIterable(findAll()).spliterator(), false)
			.filter(entity -> identifiers.contains(resolveId(entity)))
			.collect(Collectors.toSet());
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public Optional<T> findById(ID id) {

		return StreamSupport.stream(nullSafeIterable(findAll()).spliterator(), false)
			.filter(entity -> resolveId(entity).equals(id))
			.findFirst();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void deleteAll() {

		StreamSupport.stream(nullSafeIterable(findAll()).spliterator(), false)
			.filter(Objects::nonNull)
			.forEach(this::delete);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void deleteAll(Iterable<? extends T> iterable) {

		StreamSupport.stream(nullSafeIterable(iterable).spliterator(), false)
			.filter(Objects::nonNull)
			.forEach(this::delete);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void deleteById(ID id) {
		findById(id).ifPresent(this::delete);
	}

	protected <T> Iterable<T> nullSafeIterable(@Nullable Iterable<T> iterable) {
		return iterable != null ? iterable : Collections::emptyIterator;
	}

	protected abstract ID resolveId(T entity);

}
