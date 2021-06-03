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

import java.util.Optional;

/**
 * An unset identifier. Always returns {@link Optional#empty()} as value.
 *
 * @author Jens Schaude
 * @author Oliver Gierke
 */
enum Unset implements Identifier {

	UNSET;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.jdbc.core.mapping.event.Identifier#getOptionalValue()
	 */
	@Override
	public Optional<Object> getOptionalValue() {
		return Optional.empty();
	}
}
