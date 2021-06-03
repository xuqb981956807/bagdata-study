/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.data.mongodb;

import reactor.core.publisher.Mono;

import org.bson.codecs.configuration.CodecRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;

import com.mongodb.ClientSessionOptions;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Interface for factories creating reactive {@link MongoDatabase} instances.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
public interface ReactiveMongoDatabaseFactory extends CodecRegistryProvider {

	/**
	 * Creates a default {@link MongoDatabase} instance.
	 *
	 * @return
	 * @throws DataAccessException
	 */
	MongoDatabase getMongoDatabase() throws DataAccessException;

	/**
	 * Creates a {@link MongoDatabase} instance to access the database with the given name.
	 *
	 * @param dbName must not be {@literal null} or empty.
	 * @return
	 * @throws DataAccessException
	 */
	MongoDatabase getMongoDatabase(String dbName) throws DataAccessException;

	/**
	 * Exposes a shared {@link MongoExceptionTranslator}.
	 *
	 * @return will never be {@literal null}.
	 */
	PersistenceExceptionTranslator getExceptionTranslator();

	/**
	 * Get the underlying {@link CodecRegistry} used by the reactive MongoDB Java driver.
	 *
	 * @return never {@literal null}.
	 */
	@Override
	default CodecRegistry getCodecRegistry() {
		return getMongoDatabase().getCodecRegistry();
	}

	/**
	 * Obtain a {@link Mono} emitting a {@link ClientSession} for given {@link ClientSessionOptions options}.
	 *
	 * @param options must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	Mono<ClientSession> getSession(ClientSessionOptions options);

	/**
	 * Obtain a {@link ClientSession} bound instance of {@link ReactiveMongoDatabaseFactory} returning
	 * {@link MongoDatabase} instances that are aware and bound to the given session.
	 *
	 * @param session must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 2.1
	 */
	ReactiveMongoDatabaseFactory withSession(ClientSession session);
}
