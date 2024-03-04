/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BlockingConcurrentHashMap<K, V> {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlockingConcurrentHashMap.class);

	private static final class BlockingReference<T> {
		private final BlockingQueue<T> lock = new ArrayBlockingQueue<>(1);
		private volatile boolean set = false;
		private volatile T value;

		public void set(Function<?, T> supplier) {
			lock.clear();
			set = false;
			try {
				value = supplier.apply(null);
				set = true;
				try {
					//noinspection StatementWithEmptyBody
					while (lock.offer(value)) {
						// Put while waiting Threads take values
					}
				} catch (IllegalStateException ignore) {
				}
			} catch (Exception e) {
				LOGGER.warn("Unable to get result value from passed supplier.", e);
				throw e;
			}
		}

		public T get(long timeout, TimeUnit unit) throws InterruptedException {
			if (!set) {
				return lock.poll(timeout, unit);
			} else {
				return value;
			}
		}
	}

	private static final int TIMEOUT = 1;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

	private final Map<K, BlockingReference<V>> map = new ConcurrentHashMap<>();

	public void computeIfAbsent(@Nonnull K key, Function<?, V> mappingFunction) {
		map.computeIfAbsent(key, k -> new BlockingReference<>()).set(mappingFunction);
	}

	@Nullable
	public V get(@Nonnull K key) {
		try {
			return map.computeIfAbsent(key, k -> new BlockingReference<>()).get(TIMEOUT, TIMEOUT_UNIT);
		} catch (InterruptedException e) {
			LOGGER.warn("Wait for value was interrupted", e);
		}
		return null;
	}

	@Nullable
	public V remove(@Nonnull K key) {
		try {
			return map.remove(key).get(TIMEOUT, TIMEOUT_UNIT);
		} catch (InterruptedException e) {
			LOGGER.warn("Wait for value was interrupted", e);
		}
		return null;
	}
}
