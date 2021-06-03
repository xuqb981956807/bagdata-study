/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.support.atomic;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundKeyOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Atomic double backed by Redis. Uses Redis atomic increment/decrement and watch/multi/exec operations for CAS
 * operations.
 *
 * @author Jennifer Hickey
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class RedisAtomicDouble extends Number implements Serializable, BoundKeyOperations<String> {

	private static final long serialVersionUID = 1L;

	private volatile String key;

	private final ValueOperations<String, Double> operations;
	private final RedisOperations<String, Double> generalOps;

	/**
	 * Constructs a new {@link RedisAtomicDouble} instance. Uses the value existing in Redis or 0 if none is found.
	 *
	 * @param redisCounter Redis key of this counter.
	 * @param factory connection factory.
	 */
	public RedisAtomicDouble(String redisCounter, RedisConnectionFactory factory) {
		this(redisCounter, factory, null);
	}

	/**
	 * Constructs a new {@link RedisAtomicDouble} instance.
	 *
	 * @param redisCounter Redis key of this counter.
	 * @param factory connection factory.
	 * @param initialValue initial value to set if the Redis key is absent.
	 */
	public RedisAtomicDouble(String redisCounter, RedisConnectionFactory factory, double initialValue) {
		this(redisCounter, factory, Double.valueOf(initialValue));
	}

	private RedisAtomicDouble(String redisCounter, RedisConnectionFactory factory, @Nullable Double initialValue) {

		Assert.hasText(redisCounter, "a valid counter name is required");
		Assert.notNull(factory, "a valid factory is required");

		RedisTemplate<String, Double> redisTemplate = new RedisTemplate<>();
		redisTemplate.setKeySerializer(RedisSerializer.string());
		redisTemplate.setValueSerializer(new GenericToStringSerializer<>(Double.class));
		redisTemplate.setExposeConnection(true);
		redisTemplate.setConnectionFactory(factory);
		redisTemplate.afterPropertiesSet();

		this.key = redisCounter;
		this.generalOps = redisTemplate;
		this.operations = generalOps.opsForValue();

		if (initialValue == null) {
			if (this.operations.get(redisCounter) == null) {
				set(0);
			}
		} else {
			set(initialValue);
		}
	}

	/**
	 * Constructs a new {@link RedisAtomicDouble} instance. Uses the value existing in Redis or 0 if none is found.
	 *
	 * @param redisCounter Redis key of this counter.
	 * @param template the template.
	 * @see #RedisAtomicDouble(String, RedisConnectionFactory, double)
	 */
	public RedisAtomicDouble(String redisCounter, RedisOperations<String, Double> template) {
		this(redisCounter, template, null);
	}

	/**
	 * Constructs a new {@link RedisAtomicDouble} instance. Note: You need to configure the given {@code template} with
	 * appropriate {@link RedisSerializer} for the key and value. As an alternative one could use the
	 * {@link #RedisAtomicDouble(String, RedisConnectionFactory, Double)} constructor which uses appropriate default
	 * serializers.
	 *
	 * @param redisCounter Redis key of this counter.
	 * @param template the template
	 * @param initialValue initial value to set if the Redis key is absent.
	 */
	public RedisAtomicDouble(String redisCounter, RedisOperations<String, Double> template, double initialValue) {
		this(redisCounter, template, Double.valueOf(initialValue));
	}

	private RedisAtomicDouble(String redisCounter, RedisOperations<String, Double> template,
			@Nullable Double initialValue) {

		Assert.hasText(redisCounter, "a valid counter name is required");
		Assert.notNull(template, "a valid template is required");
		Assert.notNull(template.getKeySerializer(), "a valid key serializer in template is required");
		Assert.notNull(template.getValueSerializer(), "a valid value serializer in template is required");

		this.key = redisCounter;
		this.generalOps = template;
		this.operations = generalOps.opsForValue();

		if (initialValue == null) {
			if (this.operations.get(redisCounter) == null) {
				set(0);
			}
		} else {
			set(initialValue);
		}
	}

	/**
	 * Gets the current value.
	 *
	 * @return the current value.
	 */
	public double get() {

		Double value = operations.get(key);
		if (value != null) {
			return value;
		}

		throw new DataRetrievalFailureException(String.format("The key '%s' seems to no longer exist.", key));
	}

	/**
	 * Sets to the given value.
	 *
	 * @param newValue the new value.
	 */
	public void set(double newValue) {
		operations.set(key, newValue);
	}

	/**
	 * Atomically sets to the given value and returns the old value.
	 *
	 * @param newValue the new value.
	 * @return the previous value.
	 */
	public double getAndSet(double newValue) {

		Double value = operations.getAndSet(key, newValue);

		return value != null ? value : 0;
	}

	/**
	 * Atomically sets the value to the given updated value if the current value {@code ==} the expected value.
	 *
	 * @param expect the expected value.
	 * @param update the new value.
	 * @return {@literal true} if successful. {@literal false} indicates that the actual value was not equal to the
	 *         expected value.
	 */
	public boolean compareAndSet(double expect, double update) {
		return generalOps.execute(new CompareAndSet<>(this::get, this::set, key, expect, update));
	}

	/**
	 * Atomically increments by one the current value.
	 *
	 * @return the previous value
	 */
	public double getAndIncrement() {
		return incrementAndGet() - 1.0;
	}

	/**
	 * Atomically decrements by one the current value.
	 *
	 * @return the previous value
	 */
	public double getAndDecrement() {
		return decrementAndGet() + 1.0;
	}

	/**
	 * Atomically adds the given value to the current value.
	 *
	 * @param delta the value to add
	 * @return the previous value
	 */
	public double getAndAdd(double delta) {
		return addAndGet(delta) - delta;
	}

	/**
	 * Atomically increments by one the current value.
	 *
	 * @return the updated value
	 */
	public double incrementAndGet() {
		return operations.increment(key, 1.0);
	}

	/**
	 * Atomically decrements by one the current value.
	 *
	 * @return the updated value
	 */
	public double decrementAndGet() {
		return operations.increment(key, -1.0);
	}

	/**
	 * Atomically adds the given value to the current value.
	 *
	 * @param delta the value to add
	 * @return the updated value
	 */
	public double addAndGet(double delta) {
		return operations.increment(key, delta);
	}

	/**
	 * @return the String representation of the current value.
	 */
	@Override
	public String toString() {
		return Double.toString(get());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#getKey()
	 */
	@Override
	public String getKey() {
		return key;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#getType()
	 */
	@Override
	public DataType getType() {
		return DataType.STRING;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#getExpire()
	 */
	@Override
	public Long getExpire() {
		return generalOps.getExpire(key);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#expire(long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public Boolean expire(long timeout, TimeUnit unit) {
		return generalOps.expire(key, timeout, unit);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#expireAt(java.util.Date)
	 */
	@Override
	public Boolean expireAt(Date date) {
		return generalOps.expireAt(key, date);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#persist()
	 */
	@Override
	public Boolean persist() {
		return generalOps.persist(key);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.redis.core.BoundKeyOperations#rename(java.lang.Object)
	 */
	@Override
	public void rename(String newKey) {

		generalOps.rename(key, newKey);
		key = newKey;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Number#intValue()
	 */
	@Override
	public int intValue() {
		return (int) get();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Number#longValue()
	 */
	@Override
	public long longValue() {
		return (long) get();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Number#floatValue()
	 */
	@Override
	public float floatValue() {
		return (float) get();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Number#doubleValue()
	 */
	@Override
	public double doubleValue() {
		return get();
	}
}