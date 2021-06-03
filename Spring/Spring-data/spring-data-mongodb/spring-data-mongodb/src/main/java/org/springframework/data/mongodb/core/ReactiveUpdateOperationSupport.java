/*
 * Copyright 2017-2018 the original author or authors.
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

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.mongodb.client.result.UpdateResult;

/**
 * Implementation of {@link ReactiveUpdateOperation}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.0
 */
@RequiredArgsConstructor
class ReactiveUpdateOperationSupport implements ReactiveUpdateOperation {

	private static final Query ALL_QUERY = new Query();

	private final @NonNull ReactiveMongoTemplate template;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation#update(java.lang.Class)
	 */
	@Override
	public <T> ReactiveUpdate<T> update(Class<T> domainType) {

		Assert.notNull(domainType, "DomainType must not be null!");

		return new ReactiveUpdateSupport<>(template, domainType, ALL_QUERY, null, null, null, null, null, domainType);
	}

	@RequiredArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	static class ReactiveUpdateSupport<T>
			implements ReactiveUpdate<T>, UpdateWithCollection<T>, UpdateWithQuery<T>, TerminatingUpdate<T>,
			FindAndReplaceWithOptions<T>, FindAndReplaceWithProjection<T>, TerminatingFindAndReplace<T> {

		@NonNull ReactiveMongoTemplate template;
		@NonNull Class<?> domainType;
		Query query;
		org.springframework.data.mongodb.core.query.Update update;
		@Nullable String collection;
		@Nullable FindAndModifyOptions findAndModifyOptions;
		@Nullable FindAndReplaceOptions findAndReplaceOptions;
		@Nullable Object replacement;
		@NonNull Class<T> targetType;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.UpdateWithUpdate#apply(org.springframework.data.mongodb.core.query.Update)
		 */
		@Override
		public TerminatingUpdate<T> apply(org.springframework.data.mongodb.core.query.Update update) {

			Assert.notNull(update, "Update must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions,
					findAndReplaceOptions, replacement, targetType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.UpdateWithCollection#inCollection(java.lang.String)
		 */
		@Override
		public UpdateWithQuery<T> inCollection(String collection) {

			Assert.hasText(collection, "Collection must not be null nor empty!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions,
					findAndReplaceOptions, replacement, targetType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.TerminatingUpdate#first()
		 */
		@Override
		public Mono<UpdateResult> first() {
			return doUpdate(false, false);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.TerminatingUpdate#upsert()
		 */
		@Override
		public Mono<UpdateResult> upsert() {
			return doUpdate(true, true);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.TerminatingFindAndModify#findAndModify()
		 */
		@Override
		public Mono<T> findAndModify() {

			String collectionName = getCollectionName();

			return template.findAndModify(query, update, findAndModifyOptions, targetType, collectionName);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.TerminatingFindAndReplace#findAndReplace()
		 */
		@Override
		public Mono<T> findAndReplace() {
			return template.findAndReplace(query, replacement,
					findAndReplaceOptions != null ? findAndReplaceOptions : new FindAndReplaceOptions(), (Class) domainType,
					getCollectionName(), targetType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.UpdateWithQuery#matching(org.springframework.data.mongodb.core.Query)
		 */
		@Override
		public UpdateWithUpdate<T> matching(Query query) {

			Assert.notNull(query, "Query must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions,
					findAndReplaceOptions, replacement, targetType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.TerminatingUpdate#all()
		 */
		@Override
		public Mono<UpdateResult> all() {
			return doUpdate(true, false);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.FindAndModifyWithOptions#withOptions(org.springframework.data.mongodb.core.FindAndModifyOptions)
		 */
		@Override
		public TerminatingFindAndModify<T> withOptions(FindAndModifyOptions options) {

			Assert.notNull(options, "Options must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, options,
					findAndReplaceOptions, replacement, targetType);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.UpdateWithUpdate#replaceWith(java.lang.Object)
		 */
		@Override
		public FindAndReplaceWithProjection<T> replaceWith(T replacement) {

			Assert.notNull(replacement, "Replacement must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions,
					findAndReplaceOptions, replacement, targetType);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.FindAndReplaceWithOptions#withOptions(org.springframework.data.mongodb.core.FindAndReplaceOptions)
		 */
		@Override
		public FindAndReplaceWithProjection<T> withOptions(FindAndReplaceOptions options) {

			Assert.notNull(options, "Options must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions, options,
					replacement, targetType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ReactiveUpdateOperation.FindAndReplaceWithProjection#as(java.lang.Class)
		 */
		@Override
		public <R> FindAndReplaceWithOptions<R> as(Class<R> resultType) {

			Assert.notNull(resultType, "ResultType must not be null!");

			return new ReactiveUpdateSupport<>(template, domainType, query, update, collection, findAndModifyOptions,
					findAndReplaceOptions, replacement, resultType);
		}

		private Mono<UpdateResult> doUpdate(boolean multi, boolean upsert) {
			return template.doUpdate(getCollectionName(), query, update, domainType, upsert, multi);
		}

		private String getCollectionName() {
			return StringUtils.hasText(collection) ? collection : template.determineCollectionName(domainType);
		}
	}
}
