/*
 * Copyright 2011-2018 the original author or authors.
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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Polygon;
import org.springframework.data.geo.Shape;
import org.springframework.data.mongodb.core.convert.MongoConverters.AtomicIntegerToIntegerConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.AtomicLongToLongConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.BigDecimalToStringConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.CurrencyToStringConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.IntegerToAtomicIntegerConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.LongToAtomicLongConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.StringToBigDecimalConverter;
import org.springframework.data.mongodb.core.convert.MongoConverters.StringToCurrencyConverter;
import org.springframework.data.mongodb.core.geo.Sphere;

import org.bson.Document;

/**
 * Unit tests for {@link MongoConverters}.
 *
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Christoph Strobl
 */
public class MongoConvertersUnitTests {

	@Test
	public void convertsBigDecimalToStringAndBackCorrectly() {

		BigDecimal bigDecimal = BigDecimal.valueOf(254, 1);
		String value = BigDecimalToStringConverter.INSTANCE.convert(bigDecimal);
		assertThat(value, is("25.4"));

		BigDecimal reference = StringToBigDecimalConverter.INSTANCE.convert(value);
		assertThat(reference, is(bigDecimal));
	}

	@Test // DATAMONGO-858
	public void convertsBoxToDocumentAndBackCorrectly() {

		Box box = new Box(new Point(1, 2), new Point(3, 4));

		Document document = GeoConverters.BoxToDocumentConverter.INSTANCE.convert(box);
		Shape shape = GeoConverters.DocumentToBoxConverter.INSTANCE.convert(document);

		assertThat(shape, is((org.springframework.data.geo.Shape) box));
	}

	@Test // DATAMONGO-858
	public void convertsCircleToDocumentAndBackCorrectly() {

		Circle circle = new Circle(new Point(1, 2), 3);

		Document document = GeoConverters.CircleToDocumentConverter.INSTANCE.convert(circle);
		Shape shape = GeoConverters.DocumentToCircleConverter.INSTANCE.convert(document);

		assertThat(shape, is((org.springframework.data.geo.Shape) circle));
	}

	@Test // DATAMONGO-858
	public void convertsPolygonToDocumentAndBackCorrectly() {

		Polygon polygon = new Polygon(new Point(1, 2), new Point(2, 3), new Point(3, 4), new Point(5, 6));

		Document document = GeoConverters.PolygonToDocumentConverter.INSTANCE.convert(polygon);
		Shape shape = GeoConverters.DocumentToPolygonConverter.INSTANCE.convert(document);

		assertThat(shape, is((org.springframework.data.geo.Shape) polygon));
	}

	@Test // DATAMONGO-858
	public void convertsSphereToDocumentAndBackCorrectly() {

		Sphere sphere = new Sphere(new Point(1, 2), 3);

		Document document = GeoConverters.SphereToDocumentConverter.INSTANCE.convert(sphere);
		org.springframework.data.geo.Shape shape = GeoConverters.DocumentToSphereConverter.INSTANCE.convert(document);

		assertThat(shape, is((org.springframework.data.geo.Shape) sphere));
	}

	@Test // DATAMONGO-858
	public void convertsPointToListAndBackCorrectly() {

		Point point = new Point(1, 2);

		Document document = GeoConverters.PointToDocumentConverter.INSTANCE.convert(point);
		org.springframework.data.geo.Point converted = GeoConverters.DocumentToPointConverter.INSTANCE.convert(document);

		assertThat(converted, is((org.springframework.data.geo.Point) point));
	}

	@Test // DATAMONGO-1372
	public void convertsCurrencyToStringCorrectly() {
		assertThat(CurrencyToStringConverter.INSTANCE.convert(Currency.getInstance("USD")), is("USD"));
	}

	@Test // DATAMONGO-1372
	public void convertsStringToCurrencyCorrectly() {
		assertThat(StringToCurrencyConverter.INSTANCE.convert("USD"), is(Currency.getInstance("USD")));
	}

	@Test // DATAMONGO-1416
	public void convertsAtomicLongToLongCorrectly() {
		assertThat(AtomicLongToLongConverter.INSTANCE.convert(new AtomicLong(100L)), is(100L));
	}

	@Test // DATAMONGO-1416
	public void convertsAtomicIntegerToIntegerCorrectly() {
		assertThat(AtomicIntegerToIntegerConverter.INSTANCE.convert(new AtomicInteger(100)), is(100));
	}

	@Test // DATAMONGO-1416
	public void convertsLongToAtomicLongCorrectly() {
		assertThat(LongToAtomicLongConverter.INSTANCE.convert(100L), is(instanceOf(AtomicLong.class)));
	}

	@Test // DATAMONGO-1416
	public void convertsIntegerToAtomicIntegerCorrectly() {
		assertThat(IntegerToAtomicIntegerConverter.INSTANCE.convert(100), is(instanceOf(AtomicInteger.class)));
	}
}
