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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.bson.Document;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.SerializationUtils;
import org.springframework.data.util.CloseableIterator;
import org.springframework.data.util.StreamUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.mongodb.client.FindIterable;

/**
 * Implementation of {@link ExecutableFindOperation}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 2.0
 */
@RequiredArgsConstructor
class ExecutableFindOperationSupport implements ExecutableFindOperation {

	private static final Query ALL_QUERY = new Query();

	private final @NonNull MongoTemplate template;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.ExecutableFindOperation#query(java.lang.Class)
	 */
	@Override
	public <T> ExecutableFind<T> query(Class<T> domainType) {

		Assert.notNull(domainType, "DomainType must not be null!");

		return new ExecutableFindSupport<>(template, domainType, domainType, null, ALL_QUERY);
	}

	/**
	 * @param <T>
	 * @author Christoph Strobl
	 * @since 2.0
	 */
	@RequiredArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	static class ExecutableFindSupport<T>
			implements ExecutableFind<T>, FindWithCollection<T>, FindWithProjection<T>, FindWithQuery<T> {

		@NonNull MongoTemplate template;
		@NonNull Class<?> domainType;
		Class<T> returnType;
		@Nullable String collection;
		Query query;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithCollection#inCollection(java.lang.String)
		 */
		@Override
		public FindWithProjection<T> inCollection(String collection) {

			Assert.hasText(collection, "Collection name must not be null nor empty!");

			return new ExecutableFindSupport<>(template, domainType, returnType, collection, query);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithProjection#as(Class)
		 */
		@Override
		public <T1> FindWithQuery<T1> as(Class<T1> returnType) {

			Assert.notNull(returnType, "ReturnType must not be null!");

			return new ExecutableFindSupport<>(template, domainType, returnType, collection, query);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithQuery#matching(org.springframework.data.mongodb.core.query.Query)
		 */
		@Override
		public TerminatingFind<T> matching(Query query) {

			Assert.notNull(query, "Query must not be null!");

			return new ExecutableFindSupport<>(template, domainType, returnType, collection, query);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#oneValue()
		 */
		@Override
		public T oneValue() {

			List<T> result = doFind(new DelegatingQueryCursorPreparer(getCursorPreparer(query, null)).limit(2));

			if (ObjectUtils.isEmpty(result)) {
				return null;
			}

			if (result.size() > 1) {
				throw new IncorrectResultSizeDataAccessException("Query " + asString() + " returned non unique result.", 1);
			}

			return result.iterator().next();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#firstValue()
		 */
		@Override
		public T firstValue() {

			List<T> result = doFind(new DelegatingQueryCursorPreparer(getCursorPreparer(query, null)).limit(1));

			return ObjectUtils.isEmpty(result) ? null : result.iterator().next();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#all()
		 */
		@Override
		public List<T> all() {
			return doFind(null);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#stream()
		 */
		@Override
		public Stream<T> stream() {
			return StreamUtils.createStreamFromIterator(doStream());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithQuery#near(org.springframework.data.mongodb.core.query.NearQuery)
		 */
		@Override
		public TerminatingFindNear<T> near(NearQuery nearQuery) {
			return () -> template.geoNear(nearQuery, domainType, getCollectionName(), returnType);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#count()
		 */
		@Override
		public long count() {
			return template.count(query, domainType, getCollectionName());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind#exists()
		 */
		@Override
		public boolean exists() {
			return template.exists(query, domainType, getCollectionName());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.FindDistinct#distinct(java.lang.String)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public TerminatingDistinct<Object> distinct(String field) {

			Assert.notNull(field, "Field must not be null!");

			return new DistinctOperationSupport(this, field);
		}

		private List<T> doFind(@Nullable CursorPreparer preparer) {

			Document queryObject = query.getQueryObject();
			Document fieldsObject = query.getFieldsObject();

			return template.doFind(getCollectionName(), queryObject, fieldsObject, domainType, returnType,
					getCursorPreparer(query, preparer));
		}

		private List<T> doFindDistinct(String field) {

			return template.findDistinct(query, field, getCollectionName(), domainType,
					returnType == domainType ? (Class<T>) Object.class : returnType);
		}

		private CloseableIterator<T> doStream() {
			return template.doStream(query, domainType, getCollectionName(), returnType);
		}

		private CursorPreparer getCursorPreparer(Query query, @Nullable CursorPreparer preparer) {
			return preparer != null ? preparer : template.new QueryCursorPreparer(query, domainType);
		}

		private String getCollectionName() {
			return StringUtils.hasText(collection) ? collection : template.getCollectionName(domainType);
		}

		private String asString() {
			return SerializationUtils.serializeToJsonSafely(query);
		}
	}

	/**
	 * @author Christoph Strobl
	 * @since 2.0
	 */
	static class DelegatingQueryCursorPreparer implements CursorPreparer {

		private final @Nullable CursorPreparer delegate;
		private Optional<Integer> limit = Optional.empty();

		DelegatingQueryCursorPreparer(@Nullable CursorPreparer delegate) {
			this.delegate = delegate;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.CursorPreparer#prepare(com.mongodb.clientFindIterable)
		 */
		@Override
		public FindIterable<Document> prepare(FindIterable<Document> cursor) {

			FindIterable<Document> target = delegate != null ? delegate.prepare(cursor) : cursor;
			return limit.map(target::limit).orElse(target);
		}

		CursorPreparer limit(int limit) {

			this.limit = Optional.of(limit);
			return this;
		}
	}

	/**
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	static class DistinctOperationSupport<T> implements TerminatingDistinct<T> {

		private final String field;
		private final ExecutableFindSupport<T> delegate;

		public DistinctOperationSupport(ExecutableFindSupport<T> delegate, String field) {

			this.delegate = delegate;
			this.field = field;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.DistinctWithProjection#as(java.lang.Class)
		 */
		@Override
		@SuppressWarnings("unchecked")
		public <R> TerminatingDistinct<R> as(Class<R> resultType) {

			Assert.notNull(resultType, "ResultType must not be null!");

			return new DistinctOperationSupport<>((ExecutableFindSupport) delegate.as(resultType), field);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.DistinctWithQuery#matching(org.springframework.data.mongodb.core.query.Query)
		 */
		@Override
		public TerminatingDistinct<T> matching(Query query) {

			Assert.notNull(query, "Query must not be null!");

			return new DistinctOperationSupport<>((ExecutableFindSupport<T>) delegate.matching(query), field);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingDistinct#all()
		 */
		@Override
		public List<T> all() {
			return delegate.doFindDistinct(field);
		}
	}
}
