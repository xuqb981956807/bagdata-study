/*
 * Copyright 2015-2017 the original author or authors.
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
package org.springframework.data.projection;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Unit tests for {@link DefaultProjectionInformation}.
 * 
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class DefaultProjectionInformationUnitTests {

	@Test // DATACMNS-89
	public void discoversInputProperties() {

		ProjectionInformation information = new DefaultProjectionInformation(CustomerProjection.class);

		assertThat(toNames(information.getInputProperties()), contains("firstname", "lastname"));
	}

	@Test // DATACMNS-1206, DATACMNS-1284
	public void omitsInputPropertiesAcceptingArguments() {

		ProjectionInformation information = new DefaultProjectionInformation(ProjectionAcceptingArguments.class);

		assertThat(toNames(information.getInputProperties()), contains("lastname"));
	}

	@Test // DATACMNS-89
	public void discoversAllInputProperties() {

		ProjectionInformation information = new DefaultProjectionInformation(ExtendedProjection.class);

		assertThat(toNames(information.getInputProperties()), contains("age", "firstname", "lastname"));
	}

	@Test // DATACMNS-1206, DATACMNS-1284
	public void discoversInputPropertiesInOrder() {

		ProjectionInformation information = new DefaultProjectionInformation(SameMethodNamesInAlternateOrder.class);

		assertThat(toNames(information.getInputProperties()), contains("firstname", "lastname"));
	}

	@Test // DATACMNS-1206, DATACMNS-1284
	public void discoversAllInputPropertiesInOrder() {

		assertThat(toNames(new DefaultProjectionInformation(CompositeProjection.class).getInputProperties()),
				contains("firstname", "lastname", "age"));
		assertThat(toNames(new DefaultProjectionInformation(ReorderedCompositeProjection.class).getInputProperties()),
				contains("age", "firstname", "lastname"));
	}

	private static List<String> toNames(List<PropertyDescriptor> descriptors) {

		List<String> names = new ArrayList<String>(descriptors.size());

		for (PropertyDescriptor descriptor : descriptors) {
			names.add(descriptor.getName());
		}

		return names;
	}

	interface CustomerProjection {

		String getFirstname();

		String getLastname();
	}

	interface ProjectionAcceptingArguments {

		@Value("foo")
		String getFirstname(int i);

		String getLastname();
	}

	interface ExtendedProjection extends CustomerProjection {

		int getAge();
	}

	interface SameMethodNamesInAlternateOrder {

		String getFirstname();

		String getLastname();

		String getFirstname(String foo);
	}

	interface CompositeProjection extends CustomerProjection, AgeProjection {}

	interface ReorderedCompositeProjection extends AgeProjection, CustomerProjection {}

	interface AgeProjection {

		int getAge();
	}
}
