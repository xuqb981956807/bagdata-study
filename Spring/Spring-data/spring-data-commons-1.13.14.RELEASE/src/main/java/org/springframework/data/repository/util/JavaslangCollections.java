/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.data.repository.util;

import javaslang.collection.Traversable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.repository.util.QueryExecutionConverters.WrapperType;
import org.springframework.util.ReflectionUtils;

/**
 * Converter implementations to map from and to Javaslang collections.
 * 
 * @author Oliver Gierke
 * @since 1.13
 */
class JavaslangCollections {

	public enum ToJavaConverter implements Converter<Object, Object> {

		INSTANCE;

		public WrapperType getWrapperType() {
			return WrapperType.multiValue(javaslang.collection.Traversable.class);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Object convert(Object source) {

			if (source instanceof javaslang.collection.Seq) {
				return ((javaslang.collection.Seq<?>) source).toJavaList();
			}

			if (source instanceof javaslang.collection.Map) {
				return ((javaslang.collection.Map<?, ?>) source).toJavaMap();
			}

			if (source instanceof javaslang.collection.Set) {
				return ((javaslang.collection.Set<?>) source).toJavaSet();
			}

			throw new IllegalArgumentException("Unsupported Javaslang collection " + source.getClass());
		}
	}

	public enum FromJavaConverter implements ConditionalGenericConverter {

		INSTANCE {

			/* 
			 * (non-Javadoc)
			 * @see org.springframework.core.convert.converter.GenericConverter#getConvertibleTypes()
			 */
			@Override
			public java.util.Set<ConvertiblePair> getConvertibleTypes() {
				return CONVERTIBLE_PAIRS;
			}

			/* 
			 * (non-Javadoc)
			 * @see org.springframework.core.convert.converter.ConditionalConverter#matches(org.springframework.core.convert.TypeDescriptor, org.springframework.core.convert.TypeDescriptor)
			 */
			@Override
			public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {

				// Prevent collections to be mapped to maps
				if (sourceType.isCollection() && javaslang.collection.Map.class.isAssignableFrom(targetType.getType())) {
					return false;
				}

				// Prevent maps to be mapped to collections
				if (sourceType.isMap() && !(javaslang.collection.Map.class.isAssignableFrom(targetType.getType())
						|| targetType.getType().equals(Traversable.class))) {
					return false;
				}

				return true;
			}

			/* 
			* (non-Javadoc)
			* @see org.springframework.core.convert.converter.GenericConverter#convert(java.lang.Object, org.springframework.core.convert.TypeDescriptor, org.springframework.core.convert.TypeDescriptor)
			*/
			@Override
			public Object convert(Object source, TypeDescriptor sourceDescriptor, TypeDescriptor targetDescriptor) {

				Class<?> targetType = targetDescriptor.getType();

				if (javaslang.collection.Seq.class.isAssignableFrom(targetType)) {
					return ReflectionUtils.invokeMethod(LIST_FACTORY_METHOD, null, source);
				}

				if (javaslang.collection.Set.class.isAssignableFrom(targetType)) {
					return ReflectionUtils.invokeMethod(SET_FACTORY_METHOD, null, source);
				}

				if (javaslang.collection.Map.class.isAssignableFrom(targetType)) {
					return ReflectionUtils.invokeMethod(MAP_FACTORY_METHOD, null, source);
				}

				// No dedicated type asked for, probably Traversable.
				// Try to stay as close to the source value.

				if (source instanceof List) {
					return ReflectionUtils.invokeMethod(LIST_FACTORY_METHOD, null, source);
				}

				if (source instanceof Set) {
					return ReflectionUtils.invokeMethod(SET_FACTORY_METHOD, null, source);
				}

				if (source instanceof Map) {
					return ReflectionUtils.invokeMethod(MAP_FACTORY_METHOD, null, source);
				}

				return source;
			}
		};

		private static final Set<ConvertiblePair> CONVERTIBLE_PAIRS;
		private static final Method LIST_FACTORY_METHOD;
		private static final Method SET_FACTORY_METHOD;
		private static final Method MAP_FACTORY_METHOD;

		static {

			Set<ConvertiblePair> pairs = new HashSet<ConvertiblePair>();
			pairs.add(new ConvertiblePair(Collection.class, javaslang.collection.Traversable.class));
			pairs.add(new ConvertiblePair(Map.class, javaslang.collection.Traversable.class));

			CONVERTIBLE_PAIRS = Collections.unmodifiableSet(pairs);

			MAP_FACTORY_METHOD = ReflectionUtils.findMethod(javaslang.collection.LinkedHashMap.class, "ofAll", Map.class);
			LIST_FACTORY_METHOD = ReflectionUtils.findMethod(javaslang.collection.List.class, "ofAll", Iterable.class);
			SET_FACTORY_METHOD = ReflectionUtils.findMethod(javaslang.collection.LinkedHashSet.class, "ofAll",
					Iterable.class);
		}
	}
}
