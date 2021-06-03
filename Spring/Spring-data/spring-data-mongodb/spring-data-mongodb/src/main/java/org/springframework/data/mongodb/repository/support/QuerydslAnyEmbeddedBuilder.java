/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.data.mongodb.repository.support;

import java.util.Collection;

import com.querydsl.core.support.QueryMixin;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.mongodb.MongodbOps;

/**
 * {@code QuerydslAnyEmbeddedBuilder} is a builder for constraints on embedded objects.
 * <p>
 * Original implementation source {@link com.querydsl.mongodb.AnyEmbeddedBuilder} by {@literal The Querydsl Team}
 * (<a href="http://www.querydsl.com/team">http://www.querydsl.com/team</a>) licensed under the Apache License, Version
 * 2.0.
 * </p>
 * Modified for usage with {@link QuerydslAbstractMongodbQuery}.
 * 
 * @param <Q> query type
 * @author tiwe
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 2.1
 */
public class QuerydslAnyEmbeddedBuilder<Q extends QuerydslAbstractMongodbQuery<K, Q>, K> {

	private final QueryMixin<Q> queryMixin;
	private final Path<? extends Collection<?>> collection;

	QuerydslAnyEmbeddedBuilder(QueryMixin<Q> queryMixin, Path<? extends Collection<?>> collection) {

		this.queryMixin = queryMixin;
		this.collection = collection;
	}

	/**
	 * Add the given where conditions.
	 *
	 * @param conditions must not be {@literal null}.
	 * @return the target {@link QueryMixin}.
	 * @see QueryMixin#where(Predicate)
	 */
	public Q on(Predicate... conditions) {

		return queryMixin
				.where(ExpressionUtils.predicate(MongodbOps.ELEM_MATCH, collection, ExpressionUtils.allOf(conditions)));
	}
}
