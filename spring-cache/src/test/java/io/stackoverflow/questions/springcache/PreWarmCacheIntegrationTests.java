package io.stackoverflow.questions.springcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CacheableService;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.BaseCrudRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration Tests demonstrating how to pre-warm a Spring {@link Cache}.
 *
 * This example/test code answers a question on StackOverflow.
 *
 * @author John Blum
 * @see org.springframework.cache.Cache
 * @see org.springframework.cache.CacheManager
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CachePut
 * @see org.springframework.cache.annotation.EnableCaching
 * @see <a href="https://stackoverflow.com/questions/64101012/how-to-get-individual-item-by-key-from-cache-in-spring-cache-in-spring-boot/64108090">How to get individual item by key from cache in Spring cache in Spring Boot?</a>
 * @since 1.0.0
 */
@ActiveProfiles("PreWarmCacheUsingService")
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class PreWarmCacheIntegrationTests {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserService userService;

	@Before
	public void setup() {

		assertThat(this.userRepository).isNotNull();
		assertThat(this.userRepository.count()).isEqualTo(2);

		// These users will not be in the cache on startup and will result in a cache miss
		this.userRepository.saveAll(Arrays.asList(
			User.newUser("Pie Doe").identifiedBy(3L),
			User.newUser("Sour Doe").identifiedBy(4L)
		));

		assertThat(this.userRepository.count()).isEqualTo(4);

		assertThat(this.userService).isNotNull();
		assertThat(this.userService.isCacheMiss()).isFalse();

		reset(this.userRepository);
	}

	@Test
	public void userAccessResultingInExpectedCacheHitsAndMisses() {

		User janeDoe = User.newUser("Jane Doe").identifiedBy(2L);
		User sourDoe = User.newUser("Sour Doe").identifiedBy(4L);

		// test and assert cache hits
		assertThat(this.userService.findById(janeDoe.getId())).isEqualTo(janeDoe);
		assertThat(this.userService.isCacheHit()).isTrue();

		verifyNoInteractions(this.userRepository);

		// test and assert cache miss followed by a subsequent cache hit
		assertThat(this.userService.findById(sourDoe.getId())).isEqualTo(sourDoe);
		assertThat(this.userService.isCacheHit()).isFalse();
		assertThat(this.userService.findById(sourDoe.getId())).isEqualTo(sourDoe);
		assertThat(this.userService.isCacheHit()).isTrue();

		verify(this.userRepository, times(1)).findById(eq(sourDoe.getId()));
		verify(this.userRepository, times(1)).findAll(); // findById(..) is implemented in terms of findAll()
	}

	@Configuration
	@EnableCaching
	static class PreWarmCacheBaseConfiguration {

		@Bean
		ConcurrentMapCacheManager cacheManager() {

			ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("UsersById");

			cacheManager.setAllowNullValues(false);

			return cacheManager;
		}

		@Bean
		UserRepository userRepository() {

			UserRepository userRepository = new MockUserRepository();

			// Pre-initialize the backend data store with (existing) data
			// These Users (data) will also be used to pre-warm the cache and will result in a cache hit
			userRepository.saveAll(Arrays.asList(
				User.newUser("Jon Doe").identifiedBy(1L),
				User.newUser("Jane Doe").identifiedBy(2L)
			));

			return spy(userRepository);
		}

		@Bean
		UserService userService(UserRepository userRepository) {
			return new UserService(userRepository);
		}
	}

	@Configuration
	@Profile("PreWarmCacheUsingService")
	static class PreWarmCacheUsingServiceConfiguration {

		@EventListener(ContextRefreshedEvent.class)
		public void preWarmCacheOnStartup(ContextRefreshedEvent event) {

			UserService userService =
				event.getApplicationContext().getBean("userService", UserService.class);

			userService.userDirectory().forEach(userService::cache);
		}
	}

	@Configuration
	@Profile(("PreWarmCacheUsingCacheManagerAndRepository"))
	static class PreWarmCacheUsingCacheManagerAndRepositoryConfiguration {

		@EventListener(ContextRefreshedEvent.class)
		public void preWarmCacheOnStartup(ContextRefreshedEvent event) {

			ApplicationContext applicationContext = event.getApplicationContext();

			CacheManager cacheManager = applicationContext.getBean(CacheManager.class);

			UserRepository userRepository = applicationContext.getBean("userRepository", UserRepository.class);

			Cache usersById = cacheManager.getCache("UsersById");

			if (usersById != null) {
				StreamSupport.stream(userRepository.findAll().spliterator(), false)
					.filter(Objects::nonNull)
					.forEach(user -> usersById.put(user.getId(), user));
			}
		}
	}

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "newUser")
	static class User {

		@Id
		private Long id;

		@NonNull
		private final String name;

		User identifiedBy(Long id) {
			this.id = id;
			return this;
		}
	}

	interface UserRepository extends CrudRepository<User, Long> { }

	@Repository
	static class MockUserRepository extends BaseCrudRepository<User, Long> implements UserRepository {

		private static final Map<Long, User> users = new ConcurrentHashMap<>();

		@Override
		public <S extends User> S save(S user) {
			users.put(user.getId(), user);
			return user;
		}

		@Override
		public Iterable<User> findAll() {
			return Collections.unmodifiableCollection(users.values());
		}

		@Override
		public void delete(User user) {
			users.remove(user.getId());
		}

		@Override
		protected Long resolveId(User user) {
			return user.getId();
		}
	}

	@Service
	static class UserService extends CacheableService {

		private final UserRepository userRepository;

		UserService(UserRepository userRepository) {

			Assert.notNull(userRepository, "UserRepository is required");

			this.userRepository = userRepository;
		}

		@CachePut(cacheNames = "UsersById", key = "#user.id")
		public User cache(User user) {
			return user;
		}

		@Cacheable("UsersById")
		public User findById(Long id) {
			setCacheMiss();
			return this.userRepository.findById(id).orElse(null);

		}

		// Cache User then Write-Through to the backend data store
		@CachePut(cacheNames = "UsersById", key = "#user.id")
		public User save(User user) {
			return this.userRepository.save(user);
		}

		public Iterable<User> userDirectory() {
			return this.userRepository.findAll();
		}
	}
}
