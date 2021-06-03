/*
 * Copyright 2014-2018 the original author or authors.
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
package org.springframework.data.mongodb.core.convert;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * A path of objects nested into each other. The type allows access to all parent objects currently in creation even
 * when resolving more nested objects. This allows to avoid re-resolving object instances that are logically equivalent
 * to already resolved ones.
 * <p>
 * An immutable ordered set of target objects for {@link org.bson.Document} to {@link Object} conversions. Object paths
 * can be extended via {@link #push(Object, MongoPersistentEntity, Object)}.
 *
 * @author Thomas Darimont
 * @author Oliver Gierke
 * @author Mark Paluch
 * @author Christoph Strobl
 * @since 1.6
 */
class ObjectPath {

	static final ObjectPath ROOT = new ObjectPath();

	private final ObjectPathItem[] items;

	private ObjectPath() {
		this.items = new ObjectPathItem[0];
	}

	/**
	 * Creates a new {@link ObjectPath} from the given parent {@link ObjectPath} by adding the provided
	 * {@link ObjectPathItem} to it.
	 *
	 * @param parent must not be {@literal null}.
	 * @param item
	 */
	private ObjectPath(ObjectPath parent, ObjectPath.ObjectPathItem item) {

		ObjectPathItem[] items = new ObjectPathItem[parent.items.length + 1];
		System.arraycopy(parent.items, 0, items, 0, parent.items.length);
		items[parent.items.length] = item;

		this.items = items;
	}

	/**
	 * Returns a copy of the {@link ObjectPath} with the given {@link Object} as current object.
	 *
	 * @param object must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @param id must not be {@literal null}.
	 * @return new instance of {@link ObjectPath}.
	 */
	ObjectPath push(Object object, MongoPersistentEntity<?> entity, @Nullable Object id) {

		Assert.notNull(object, "Object must not be null!");
		Assert.notNull(entity, "MongoPersistentEntity must not be null!");

		ObjectPathItem item = new ObjectPathItem(object, id, entity.getCollection());
		return new ObjectPath(this, item);
	}

	/**
	 * Returns the object with the given id and stored in the given collection if it's contained in the
	 * {@link ObjectPath}.
	 *
	 * @param id must not be {@literal null}.
	 * @param collection must not be {@literal null} or empty.
	 * @return
	 * @deprecated use {@link #getPathItem(Object, String, Class)}.
	 */
	@Nullable
	@Deprecated
	Object getPathItem(Object id, String collection) {

		Assert.notNull(id, "Id must not be null!");
		Assert.hasText(collection, "Collection name must not be null!");

		for (ObjectPathItem item : items) {

			Object object = item.getObject();

			if (object == null || item.getIdValue() == null) {
				continue;
			}

			if (collection.equals(item.getCollection()) && id.equals(item.getIdValue())) {
				return object;
			}
		}

		return null;
	}

	/**
	 * Get the object with given {@literal id}, stored in the {@literal collection} that is assignable to the given
	 * {@literal type} or {@literal null} if no match found.
	 *
	 * @param id must not be {@literal null}.
	 * @param collection must not be {@literal null} or empty.
	 * @param type must not be {@literal null}.
	 * @return {@literal null} when no match found.
	 * @since 2.0
	 */
	@Nullable
	<T> T getPathItem(Object id, String collection, Class<T> type) {

		Assert.notNull(id, "Id must not be null!");
		Assert.hasText(collection, "Collection name must not be null!");
		Assert.notNull(type, "Type must not be null!");

		for (ObjectPathItem item : items) {

			Object object = item.getObject();

			if (object == null || item.getIdValue() == null) {
				continue;
			}

			if (collection.equals(item.getCollection()) && id.equals(item.getIdValue())
					&& ClassUtils.isAssignable(type, object.getClass())) {
				return type.cast(object);
			}
		}

		return null;
	}

	/**
	 * Returns the current object of the {@link ObjectPath} or {@literal null} if the path is empty.
	 *
	 * @return
	 */
	@Nullable
	Object getCurrentObject() {
		return items.length == 0 ? null : items[items.length - 1].getObject();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		if (items.length == 0) {
			return "[empty]";
		}

		List<String> strings = new ArrayList<>(items.length);

		for (ObjectPathItem item : items) {
			strings.add(ObjectUtils.nullSafeToString(item.object));
		}

		return StringUtils.collectionToDelimitedString(strings, " -> ");
	}

	/**
	 * An item in an {@link ObjectPath}.
	 *
	 * @author Thomas Darimont
	 * @author Oliver Gierke
	 * @author Mark Paluch
	 */
	@Value
	private static class ObjectPathItem {

		Object object;
		@Nullable Object idValue;
		String collection;
	}
}
