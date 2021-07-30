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
package io.stackoverflow.questions.java.lang;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests testing the concurrent behavior of the Java language.
 *
 * @author John Blum
 * @see java.lang.Runnable
 * @see java.lang.Thread
 * @since 1.0.0
 */
public class ConcurrentApplicationUnitTests {

	private static boolean proceed;

	private static int number;

	public static void main(String[] args) {

		assertThread("main");

		new Thread(new ReaderRunnable(), "Reader Thread").start();

		number = 42;
		proceed = true;
	}

	private static void assertThread(String expectedName) {

		Thread currentThread = Thread.currentThread();

		assertThat(currentThread).isNotNull();
		assertThat(currentThread.isAlive()).isTrue();
		assertThat(currentThread.isDaemon()).isFalse();
		assertThat(currentThread.getName()).isEqualTo(expectedName);
		assertThat(currentThread.getPriority()).isEqualTo(Thread.NORM_PRIORITY);
		assertThat(currentThread.getState()).isEqualTo(Thread.State.RUNNABLE);
	}

	private static final class ReaderRunnable implements Runnable {

		@Override
		public void run() {

			assertThread("Reader Thread");

			while (!proceed) {
				Thread.yield();
			}

			System.out.printf("Number is [%d]%n", number);
		}
	}
}
