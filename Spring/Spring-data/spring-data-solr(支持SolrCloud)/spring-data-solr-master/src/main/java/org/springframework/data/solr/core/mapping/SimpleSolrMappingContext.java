/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.solr.core.mapping;

import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.solr.core.schema.SolrPersistentEntitySchemaCreator;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

/**
 * Solr specific implementation of {@link org.springframework.data.mapping.context.MappingContext}
 * 
 * @author Christoph Strobl
 */
public class SimpleSolrMappingContext
		extends AbstractMappingContext<SimpleSolrPersistentEntity<?>, SolrPersistentProperty> {

	public SimpleSolrMappingContext() {
		this(null);
	}

	public SimpleSolrMappingContext(@Nullable SolrPersistentEntitySchemaCreator schemaCreator) {
		if (schemaCreator != null) {
			setApplicationEventPublisher(new SolrMappingEventPublisher(schemaCreator));
		}
	}

	@Override
	protected <T> SimpleSolrPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		return new SimpleSolrPersistentEntity<>(typeInformation);
	}

	@Override
	protected SolrPersistentProperty createPersistentProperty(Property property, SimpleSolrPersistentEntity<?> owner,
			SimpleTypeHolder simpleTypeHolder) {
		return new SimpleSolrPersistentProperty(property, owner, simpleTypeHolder);
	}

}
