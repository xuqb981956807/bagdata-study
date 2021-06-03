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
package org.springframework.data.mongodb.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import com.mongodb.client.model.IndexOptions;

/**
 * Default implementation of {@link ReactiveIndexOperations}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
public class DefaultReactiveIndexOperations implements ReactiveIndexOperations {

	private static final String PARTIAL_FILTER_EXPRESSION_KEY = "partialFilterExpression";

	private final ReactiveMongoOperations mongoOperations;
	private final String collectionName;
	private final QueryMapper queryMapper;
	private final Optional<Class<?>> type;

	/**
	 * Creates a new {@link DefaultReactiveIndexOperations}.
	 *
	 * @param mongoOperations must not be {@literal null}.
	 * @param collectionName must not be {@literal null}.
	 * @param queryMapper must not be {@literal null}.
	 */
	public DefaultReactiveIndexOperations(ReactiveMongoOperations mongoOperations, String collectionName,
			QueryMapper queryMapper) {
		this(mongoOperations, collectionName, queryMapper, Optional.empty());
	}

	/**
	 * Creates a new {@link DefaultReactiveIndexOperations}.
	 *
	 * @param mongoOperations must not be {@literal null}.
	 * @param collectionName must not be {@literal null}.
	 * @param queryMapper must not be {@literal null}.
	 * @param type used for mapping potential partial index filter expression, must not be {@literal null}.
	 */
	public DefaultReactiveIndexOperations(ReactiveMongoOperations mongoOperations, String collectionName,
			QueryMapper queryMapper, Class<?> type) {
		this(mongoOperations, collectionName, queryMapper, Optional.of(type));
	}

	private DefaultReactiveIndexOperations(ReactiveMongoOperations mongoOperations, String collectionName,
			QueryMapper queryMapper, Optional<Class<?>> type) {

		Assert.notNull(mongoOperations, "ReactiveMongoOperations must not be null!");
		Assert.notNull(collectionName, "Collection must not be null!");
		Assert.notNull(queryMapper, "QueryMapper must not be null!");

		this.mongoOperations = mongoOperations;
		this.collectionName = collectionName;
		this.queryMapper = queryMapper;
		this.type = type;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.index.ReactiveIndexOperations#ensureIndex(org.springframework.data.mongodb.core.index.IndexDefinition)
	 */
	public Mono<String> ensureIndex(final IndexDefinition indexDefinition) {

		return mongoOperations.execute(collectionName, collection -> {

			Document indexOptions = indexDefinition.getIndexOptions();

			IndexOptions ops = IndexConverters.indexDefinitionToIndexOptionsConverter().convert(indexDefinition);

			if (indexOptions.containsKey(PARTIAL_FILTER_EXPRESSION_KEY)) {

				Assert.isInstanceOf(Document.class, indexOptions.get(PARTIAL_FILTER_EXPRESSION_KEY));

				MongoPersistentEntity<?> entity = type
						.map(val -> (MongoPersistentEntity) queryMapper.getMappingContext().getRequiredPersistentEntity(val))
						.orElseGet(() -> lookupPersistentEntity(collectionName));

				ops = ops.partialFilterExpression(
						queryMapper.getMappedObject(indexOptions.get(PARTIAL_FILTER_EXPRESSION_KEY, Document.class), entity));
			}

			return collection.createIndex(indexDefinition.getIndexKeys(), ops);

		}).next();
	}

	@Nullable
	private MongoPersistentEntity<?> lookupPersistentEntity(String collection) {

		Collection<? extends MongoPersistentEntity<?>> entities = queryMapper.getMappingContext().getPersistentEntities();

		return entities.stream() //
				.filter(entity -> entity.getCollection().equals(collection)) //
				.findFirst() //
				.orElse(null);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.index.ReactiveIndexOperations#dropIndex(java.lang.String)
	 */
	public Mono<Void> dropIndex(final String name) {
		return mongoOperations.execute(collectionName, collection -> collection.dropIndex(name)).then();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.index.ReactiveIndexOperations#dropAllIndexes()
	 */
	public Mono<Void> dropAllIndexes() {
		return dropIndex("*");
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.index.ReactiveIndexOperations#getIndexInfo()
	 */
	public Flux<IndexInfo> getIndexInfo() {

		return mongoOperations.execute(collectionName, collection -> collection.listIndexes(Document.class)) //
				.map(IndexConverters.documentToIndexInfoConverter()::convert);
	}
}
