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
package org.springframework.data.mongodb.core.query;

import org.bson.Document;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.springframework.data.mongodb.core.DocumentTestUtils;

/**
 * Unit tests for {@link TextCriteria}.
 *
 * @author Christoph Strobl
 */
public class TextCriteriaUnitTests {

	@Test // DATAMONGO-850
	public void shouldNotHaveLanguageField() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage();
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ }")));
	}

	@Test // DATAMONGO-850
	public void shouldNotHaveLanguageForNonDefaultLanguageField() {

		TextCriteria criteria = TextCriteria.forLanguage("spanish");
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ \"$language\" : \"spanish\" }")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateSearchFieldForSingleTermCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matching("cake");
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ \"$search\" : \"cake\" }")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateSearchFieldCorrectlyForMultipleTermsCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matchingAny("bake", "coffee", "cake");
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ \"$search\" : \"bake coffee cake\" }")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateSearchFieldForPhraseCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matchingPhrase("coffee cake");
		Assert.assertThat(DocumentTestUtils.getAsDocument(criteria.getCriteriaObject(), "$text"),
				IsEqual.<Document> equalTo(new Document("$search", "\"coffee cake\"")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateNotFieldCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().notMatching("cake");
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ \"$search\" : \"-cake\" }")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateSearchFieldCorrectlyForNotMultipleTermsCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().notMatchingAny("bake", "coffee", "cake");
		assertThat(criteria.getCriteriaObject(), equalTo(searchObject("{ \"$search\" : \"-bake -coffee -cake\" }")));
	}

	@Test // DATAMONGO-850
	public void shouldCreateSearchFieldForNotPhraseCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().notMatchingPhrase("coffee cake");
		Assert.assertThat(DocumentTestUtils.getAsDocument(criteria.getCriteriaObject(), "$text"),
				IsEqual.<Document> equalTo(new Document("$search", "-\"coffee cake\"")));
	}

	@Test // DATAMONGO-1455
	public void caseSensitiveOperatorShouldBeSetCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matching("coffee").caseSensitive(true);
		assertThat(DocumentTestUtils.getAsDocument(criteria.getCriteriaObject(), "$text"),
				equalTo(new Document("$search", "coffee").append("$caseSensitive", true)));
	}

	@Test // DATAMONGO-1456
	public void diacriticSensitiveOperatorShouldBeSetCorrectly() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matching("coffee").diacriticSensitive(true);
		assertThat(DocumentTestUtils.getAsDocument(criteria.getCriteriaObject(), "$text"),
				equalTo(new Document("$search", "coffee").append("$diacriticSensitive", true)));
	}

	private Document searchObject(String json) {
		return new Document("$text", Document.parse(json));
	}

}
