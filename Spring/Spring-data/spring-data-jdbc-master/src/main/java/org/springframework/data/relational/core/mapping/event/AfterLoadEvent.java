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
package org.springframework.data.relational.core.mapping.event;

import org.springframework.data.relational.core.mapping.event.Identifier.Specified;

/**
 * Gets published after instantiation and setting of all the properties of an entity. This allows to do some
 * postprocessing of entities.
 *
 * @author Jens Schauder
 */
public class AfterLoadEvent extends RelationalEventWithIdAndEntity {

	private static final long serialVersionUID = -4185777271143436728L;

	/**
	 * @param id of the entity
	 * @param entity the newly instantiated entity.
	 */
	public AfterLoadEvent(Specified id, Object entity) {
		super(id, entity, null);
	}
}
