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

import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.convert.EntityInstantiator;
import org.springframework.data.convert.TypeMapper;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.DefaultSpELExpressionEvaluator;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mapping.model.PersistentEntityParameterValueProvider;
import org.springframework.data.mapping.model.PropertyValueProvider;
import org.springframework.data.mapping.model.SpELContext;
import org.springframework.data.mapping.model.SpELExpressionEvaluator;
import org.springframework.data.mapping.model.SpELExpressionParameterValueProvider;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.MongoMappingEvent;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBRef;

/**
 * {@link MongoConverter} that uses a {@link MappingContext} to do sophisticated mapping of domain objects to
 * {@link Document}.
 *
 * @author Oliver Gierke
 * @author Jon Brisbin
 * @author Patrik Wasik
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Jordi Llach
 * @author Mark Paluch
 */
public class MappingMongoConverter extends AbstractMongoConverter implements ApplicationContextAware, ValueResolver {

	private static final String INCOMPATIBLE_TYPES = "Cannot convert %1$s of type %2$s into an instance of %3$s! Implement a custom Converter<%2$s, %3$s> and register it with the CustomConversions. Parent object was: %4$s";
	private static final String INVALID_TYPE_TO_READ = "Expected to read Document %s into type %s but didn't find a PersistentEntity for the latter!";

	protected static final Logger LOGGER = LoggerFactory.getLogger(MappingMongoConverter.class);

	protected final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
	protected final QueryMapper idMapper;
	protected final DbRefResolver dbRefResolver;
	protected final DefaultDbRefProxyHandler dbRefProxyHandler;

	protected @Nullable ApplicationContext applicationContext;
	protected MongoTypeMapper typeMapper;
	protected @Nullable String mapKeyDotReplacement = null;

	private SpELContext spELContext;

	/**
	 * Creates a new {@link MappingMongoConverter} given the new {@link DbRefResolver} and {@link MappingContext}.
	 *
	 * @param dbRefResolver must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	public MappingMongoConverter(DbRefResolver dbRefResolver,
			MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext) {

		super(new DefaultConversionService());

		Assert.notNull(dbRefResolver, "DbRefResolver must not be null!");
		Assert.notNull(mappingContext, "MappingContext must not be null!");

		this.dbRefResolver = dbRefResolver;
		this.mappingContext = mappingContext;
		this.typeMapper = new DefaultMongoTypeMapper(DefaultMongoTypeMapper.DEFAULT_TYPE_KEY, mappingContext);
		this.idMapper = new QueryMapper(this);

		this.spELContext = new SpELContext(DocumentPropertyAccessor.INSTANCE);
		this.dbRefProxyHandler = new DefaultDbRefProxyHandler(spELContext, mappingContext, MappingMongoConverter.this);
	}

	/**
	 * Creates a new {@link MappingMongoConverter} given the new {@link MongoDbFactory} and {@link MappingContext}.
	 *
	 * @deprecated use the constructor taking a {@link DbRefResolver} instead.
	 * @param mongoDbFactory must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	@Deprecated
	public MappingMongoConverter(MongoDbFactory mongoDbFactory,
			MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext) {
		this(new DefaultDbRefResolver(mongoDbFactory), mappingContext);
	}

	/**
	 * Configures the {@link MongoTypeMapper} to be used to add type information to {@link Document}s created by the
	 * converter and how to lookup type information from {@link Document}s when reading them. Uses a
	 * {@link DefaultMongoTypeMapper} by default. Setting this to {@literal null} will reset the {@link TypeMapper} to the
	 * default one.
	 *
	 * @param typeMapper the typeMapper to set. Can be {@literal null}.
	 */
	public void setTypeMapper(@Nullable MongoTypeMapper typeMapper) {
		this.typeMapper = typeMapper == null
				? new DefaultMongoTypeMapper(DefaultMongoTypeMapper.DEFAULT_TYPE_KEY, mappingContext)
				: typeMapper;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.convert.MongoConverter#getTypeMapper()
	 */
	@Override
	public MongoTypeMapper getTypeMapper() {
		return this.typeMapper;
	}

	/**
	 * Configure the characters dots potentially contained in a {@link Map} shall be replaced with. By default we don't do
	 * any translation but rather reject a {@link Map} with keys containing dots causing the conversion for the entire
	 * object to fail. If further customization of the translation is needed, have a look at
	 * {@link #potentiallyEscapeMapKey(String)} as well as {@link #potentiallyUnescapeMapKey(String)}.
	 *
	 * @param mapKeyDotReplacement the mapKeyDotReplacement to set. Can be {@literal null}.
	 */
	public void setMapKeyDotReplacement(@Nullable String mapKeyDotReplacement) {
		this.mapKeyDotReplacement = mapKeyDotReplacement;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.convert.EntityConverter#getMappingContext()
	 */
	public MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> getMappingContext() {
		return mappingContext;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		this.applicationContext = applicationContext;
		this.spELContext = new SpELContext(this.spELContext, applicationContext);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.core.MongoReader#read(java.lang.Class, com.mongodb.Document)
	 */
	public <S extends Object> S read(Class<S> clazz, final Bson bson) {
		return read(ClassTypeInformation.from(clazz), bson);
	}

	protected <S extends Object> S read(TypeInformation<S> type, Bson bson) {
		return read(type, bson, ObjectPath.ROOT);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private <S extends Object> S read(TypeInformation<S> type, @Nullable Bson bson, ObjectPath path) {

		if (null == bson) {
			return null;
		}

		TypeInformation<? extends S> typeToUse = typeMapper.readType(bson, type);
		Class<? extends S> rawType = typeToUse.getType();

		if (conversions.hasCustomReadTarget(bson.getClass(), rawType)) {
			return conversionService.convert(bson, rawType);
		}

		if (DBObject.class.isAssignableFrom(rawType)) {
			return (S) bson;
		}

		if (Document.class.isAssignableFrom(rawType)) {
			return (S) bson;
		}

		if (typeToUse.isCollectionLike() && bson instanceof List) {
			return (S) readCollectionOrArray(typeToUse, (List<?>) bson, path);
		}

		if (typeToUse.isMap()) {
			return (S) readMap(typeToUse, bson, path);
		}

		if (bson instanceof Collection) {
			throw new MappingException(String.format(INCOMPATIBLE_TYPES, bson, BasicDBList.class, typeToUse.getType(), path));
		}

		if (typeToUse.equals(ClassTypeInformation.OBJECT)) {
			return (S) bson;
		}
		// Retrieve persistent entity info

		Document target = bson instanceof BasicDBObject ? new Document((BasicDBObject) bson) : (Document) bson;

		MongoPersistentEntity<?> entity = mappingContext.getPersistentEntity(typeToUse);

		if (entity == null) {
			throw new MappingException(String.format(INVALID_TYPE_TO_READ, target, typeToUse.getType()));
		}

		return read((MongoPersistentEntity<S>) mappingContext.getRequiredPersistentEntity(typeToUse), target, path);
	}

	private ParameterValueProvider<MongoPersistentProperty> getParameterProvider(MongoPersistentEntity<?> entity,
			Bson source, SpELExpressionEvaluator evaluator, ObjectPath path) {

		AssociationAwareMongoDbPropertyValueProvider provider = new AssociationAwareMongoDbPropertyValueProvider(source,
				evaluator, path);
		PersistentEntityParameterValueProvider<MongoPersistentProperty> parameterProvider = new PersistentEntityParameterValueProvider<>(
				entity, provider, path.getCurrentObject());

		return new ConverterAwareSpELExpressionParameterValueProvider(evaluator, conversionService, parameterProvider,
				path);
	}

	private <S extends Object> S read(final MongoPersistentEntity<S> entity, final Document bson, final ObjectPath path) {

		SpELExpressionEvaluator evaluator = new DefaultSpELExpressionEvaluator(bson, spELContext);

		ParameterValueProvider<MongoPersistentProperty> provider = getParameterProvider(entity, bson, evaluator, path);
		EntityInstantiator instantiator = instantiators.getInstantiatorFor(entity);
		S instance = instantiator.createInstance(entity, provider);

		PersistentPropertyAccessor<S> accessor = new ConvertingPropertyAccessor<>(entity.getPropertyAccessor(instance),
				conversionService);

		DocumentAccessor documentAccessor = new DocumentAccessor(bson);

		// Make sure id property is set before all other properties

		Object rawId = readAndPopulateIdentifier(accessor, documentAccessor, entity,
				(property, id) -> readIdValue(path, evaluator, property, id));
		ObjectPath currentPath = path.push(accessor.getBean(), entity, rawId);

		MongoDbPropertyValueProvider valueProvider = new MongoDbPropertyValueProvider(documentAccessor, evaluator,
				currentPath);

		DbRefResolverCallback callback = new DefaultDbRefResolverCallback(bson, currentPath, evaluator,
				MappingMongoConverter.this);
		readProperties(entity, accessor, documentAccessor, valueProvider, callback);

		return accessor.getBean();
	}

	/**
	 * Reads the identifier from either the bean backing the {@link PersistentPropertyAccessor} or the source document in
	 * case the identifier has not be populated yet. In this case the identifier is set on the bean for further reference.
	 * 
	 * @param accessor must not be {@literal null}.
	 * @param document must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @param callback the callback to actually resolve the value for the identifier property, must not be
	 *          {@literal null}.
	 * @return
	 */
	private Object readAndPopulateIdentifier(PersistentPropertyAccessor<?> accessor, DocumentAccessor document,
			MongoPersistentEntity<?> entity, BiFunction<MongoPersistentProperty, Object, Object> callback) {

		Object rawId = document.getRawId(entity);

		if (!entity.hasIdProperty() || rawId == null) {
			return rawId;
		}

		MongoPersistentProperty idProperty = entity.getRequiredIdProperty();

		if (idProperty.isImmutable() && entity.isConstructorArgument(idProperty)) {
			return rawId;
		}

		accessor.setProperty(idProperty, callback.apply(idProperty, rawId));

		return rawId;
	}

	private Object readIdValue(ObjectPath path, SpELExpressionEvaluator evaluator, MongoPersistentProperty idProperty,
			Object rawId) {

		String expression = idProperty.getSpelExpression();
		Object resolvedValue = expression != null ? evaluator.evaluate(expression) : rawId;

		return resolvedValue != null ? readValue(resolvedValue, idProperty.getTypeInformation(), path) : null;
	}

	private void readProperties(MongoPersistentEntity<?> entity, PersistentPropertyAccessor<?> accessor,
			DocumentAccessor documentAccessor, MongoDbPropertyValueProvider valueProvider, DbRefResolverCallback callback) {

		for (MongoPersistentProperty prop : entity) {

			if (prop.isAssociation() && !entity.isConstructorArgument(prop)) {
				readAssociation(prop.getRequiredAssociation(), accessor, documentAccessor, dbRefProxyHandler, callback);
				continue;
			}

			// We skip the id property since it was already set

			if (entity.isIdProperty(prop)) {
				continue;
			}

			if (entity.isConstructorArgument(prop) || !documentAccessor.hasValue(prop)) {
				continue;
			}

			if (prop.isAssociation()) {
				readAssociation(prop.getRequiredAssociation(), accessor, documentAccessor, dbRefProxyHandler, callback);
				continue;
			}

			accessor.setProperty(prop, valueProvider.getPropertyValue(prop));
		}
	}

	private void readAssociation(Association<MongoPersistentProperty> association, PersistentPropertyAccessor<?> accessor,
			DocumentAccessor documentAccessor, DbRefProxyHandler handler, DbRefResolverCallback callback) {

		MongoPersistentProperty property = association.getInverse();
		Object value = documentAccessor.get(property);

		if (value == null) {
			return;
		}

		DBRef dbref = value instanceof DBRef ? (DBRef) value : null;
		accessor.setProperty(property, dbRefResolver.resolveDbRef(property, dbref, callback, handler));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.convert.MongoWriter#toDBRef(java.lang.Object, org.springframework.data.mongodb.core.mapping.MongoPersistentProperty)
	 */
	public DBRef toDBRef(Object object, @Nullable MongoPersistentProperty referringProperty) {

		org.springframework.data.mongodb.core.mapping.DBRef annotation;

		if (referringProperty != null) {
			annotation = referringProperty.getDBRef();
			Assert.isTrue(annotation != null, "The referenced property has to be mapped with @DBRef!");
		}

		// DATAMONGO-913
		if (object instanceof LazyLoadingProxy) {
			return ((LazyLoadingProxy) object).toDBRef();
		}

		return createDBRef(object, referringProperty);
	}

	/**
	 * Root entry method into write conversion. Adds a type discriminator to the {@link Document}. Shouldn't be called for
	 * nested conversions.
	 *
	 * @see org.springframework.data.mongodb.core.convert.MongoWriter#write(java.lang.Object, com.mongodb.Document)
	 */
	public void write(Object obj, Bson bson) {

		if (null == obj) {
			return;
		}

		Class<?> entityType = ClassUtils.getUserClass(obj.getClass());
		TypeInformation<? extends Object> type = ClassTypeInformation.from(entityType);

		Object target = obj instanceof LazyLoadingProxy ? ((LazyLoadingProxy) obj).getTarget() : obj;

		writeInternal(target, bson, type);
		if (asMap(bson).containsKey("_id") && asMap(bson).get("_id") == null) {
			removeFromMap(bson, "_id");
		}

		boolean handledByCustomConverter = conversions.hasCustomWriteTarget(entityType, Document.class);
		if (!handledByCustomConverter && !(bson instanceof Collection)) {
			typeMapper.writeType(type, bson);
		}
	}

	/**
	 * Internal write conversion method which should be used for nested invocations.
	 *
	 * @param obj
	 * @param bson
	 * @param typeHint
	 */
	@SuppressWarnings("unchecked")
	protected void writeInternal(@Nullable Object obj, Bson bson, @Nullable TypeInformation<?> typeHint) {

		if (null == obj) {
			return;
		}

		Class<?> entityType = obj.getClass();
		Optional<Class<?>> customTarget = conversions.getCustomWriteTarget(entityType, Document.class);

		if (customTarget.isPresent()) {
			Document result = conversionService.convert(obj, Document.class);
			addAllToMap(bson, result);
			return;
		}

		if (Map.class.isAssignableFrom(entityType)) {
			writeMapInternal((Map<Object, Object>) obj, bson, ClassTypeInformation.MAP);
			return;
		}

		if (Collection.class.isAssignableFrom(entityType)) {
			writeCollectionInternal((Collection<?>) obj, ClassTypeInformation.LIST, (Collection<?>) bson);
			return;
		}

		MongoPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityType);
		writeInternal(obj, bson, entity);
		addCustomTypeKeyIfNecessary(typeHint, obj, bson);
	}

	protected void writeInternal(@Nullable Object obj, Bson bson, @Nullable MongoPersistentEntity<?> entity) {

		if (obj == null) {
			return;
		}

		if (null == entity) {
			throw new MappingException("No mapping metadata found for entity of type " + obj.getClass().getName());
		}

		PersistentPropertyAccessor<?> accessor = entity.getPropertyAccessor(obj);
		DocumentAccessor dbObjectAccessor = new DocumentAccessor(bson);
		MongoPersistentProperty idProperty = entity.getIdProperty();

		if (idProperty != null && !dbObjectAccessor.hasValue(idProperty)) {

			Object value = idMapper.convertId(accessor.getProperty(idProperty));

			if (value != null) {
				dbObjectAccessor.put(idProperty, value);
			}
		}

		writeProperties(bson, entity, accessor, dbObjectAccessor, idProperty);
	}

	private void writeProperties(Bson bson, MongoPersistentEntity<?> entity, PersistentPropertyAccessor<?> accessor,
			DocumentAccessor dbObjectAccessor, @Nullable MongoPersistentProperty idProperty) {

		// Write the properties
		for (MongoPersistentProperty prop : entity) {

			if (prop.equals(idProperty) || !prop.isWritable()) {
				continue;
			}
			if (prop.isAssociation()) {
				writeAssociation(prop.getRequiredAssociation(), accessor, dbObjectAccessor);
				continue;
			}

			Object value = accessor.getProperty(prop);

			if (value == null) {
				continue;
			}

			if (!conversions.isSimpleType(value.getClass())) {
				writePropertyInternal(value, dbObjectAccessor, prop);
			} else {
				writeSimpleInternal(value, bson, prop);
			}
		}
	}

	private void writeAssociation(Association<MongoPersistentProperty> association,
			PersistentPropertyAccessor<?> accessor, DocumentAccessor dbObjectAccessor) {

		MongoPersistentProperty inverseProp = association.getInverse();

		writePropertyInternal(accessor.getProperty(inverseProp), dbObjectAccessor, inverseProp);
	}

	@SuppressWarnings({ "unchecked" })
	protected void writePropertyInternal(@Nullable Object obj, DocumentAccessor accessor, MongoPersistentProperty prop) {

		if (obj == null) {
			return;
		}

		TypeInformation<?> valueType = ClassTypeInformation.from(obj.getClass());
		TypeInformation<?> type = prop.getTypeInformation();

		if (valueType.isCollectionLike()) {
			List<Object> collectionInternal = createCollection(asCollection(obj), prop);
			accessor.put(prop, collectionInternal);
			return;
		}

		if (valueType.isMap()) {
			Bson mapDbObj = createMap((Map<Object, Object>) obj, prop);
			accessor.put(prop, mapDbObj);
			return;
		}

		if (prop.isDbReference()) {

			DBRef dbRefObj = null;

			/*
			 * If we already have a LazyLoadingProxy, we use it's cached DBRef value instead of
			 * unnecessarily initializing it only to convert it to a DBRef a few instructions later.
			 */
			if (obj instanceof LazyLoadingProxy) {
				dbRefObj = ((LazyLoadingProxy) obj).toDBRef();
			}

			dbRefObj = dbRefObj != null ? dbRefObj : createDBRef(obj, prop);

			if (null != dbRefObj) {
				accessor.put(prop, dbRefObj);
				return;
			}
		}

		/*
		 * If we have a LazyLoadingProxy we make sure it is initialized first.
		 */
		if (obj instanceof LazyLoadingProxy) {
			obj = ((LazyLoadingProxy) obj).getTarget();
		}

		// Lookup potential custom target type
		Optional<Class<?>> basicTargetType = conversions.getCustomWriteTarget(obj.getClass());

		if (basicTargetType.isPresent()) {

			accessor.put(prop, conversionService.convert(obj, basicTargetType.get()));
			return;
		}

		MongoPersistentEntity<?> entity = isSubtype(prop.getType(), obj.getClass())
				? mappingContext.getRequiredPersistentEntity(obj.getClass())
				: mappingContext.getRequiredPersistentEntity(type);

		Object existingValue = accessor.get(prop);
		Document document = existingValue instanceof Document ? (Document) existingValue : new Document();

		writeInternal(obj, document, entity);
		addCustomTypeKeyIfNecessary(ClassTypeInformation.from(prop.getRawType()), obj, document);
		accessor.put(prop, document);
	}

	private boolean isSubtype(Class<?> left, Class<?> right) {
		return left.isAssignableFrom(right) && !left.equals(right);
	}

	/**
	 * Returns given object as {@link Collection}. Will return the {@link Collection} as is if the source is a
	 * {@link Collection} already, will convert an array into a {@link Collection} or simply create a single element
	 * collection for everything else.
	 *
	 * @param source
	 * @return
	 */
	private static Collection<?> asCollection(Object source) {

		if (source instanceof Collection) {
			return (Collection<?>) source;
		}

		return source.getClass().isArray() ? CollectionUtils.arrayToList(source) : Collections.singleton(source);
	}

	/**
	 * Writes the given {@link Collection} using the given {@link MongoPersistentProperty} information.
	 *
	 * @param collection must not be {@literal null}.
	 * @param property must not be {@literal null}.
	 * @return
	 */
	protected List<Object> createCollection(Collection<?> collection, MongoPersistentProperty property) {

		if (!property.isDbReference()) {
			return writeCollectionInternal(collection, property.getTypeInformation(), new BasicDBList());
		}

		List<Object> dbList = new ArrayList<>(collection.size());

		for (Object element : collection) {

			if (element == null) {
				continue;
			}

			DBRef dbRef = createDBRef(element, property);
			dbList.add(dbRef);
		}

		return dbList;
	}

	/**
	 * Writes the given {@link Map} using the given {@link MongoPersistentProperty} information.
	 *
	 * @param map must not {@literal null}.
	 * @param property must not be {@literal null}.
	 * @return
	 */
	protected Bson createMap(Map<Object, Object> map, MongoPersistentProperty property) {

		Assert.notNull(map, "Given map must not be null!");
		Assert.notNull(property, "PersistentProperty must not be null!");

		if (!property.isDbReference()) {
			return writeMapInternal(map, new Document(), property.getTypeInformation());
		}

		Document document = new Document();

		for (Map.Entry<Object, Object> entry : map.entrySet()) {

			Object key = entry.getKey();
			Object value = entry.getValue();

			if (conversions.isSimpleType(key.getClass())) {

				String simpleKey = prepareMapKey(key.toString());
				document.put(simpleKey, value != null ? createDBRef(value, property) : null);

			} else {
				throw new MappingException("Cannot use a complex object as a key value.");
			}
		}

		return document;
	}

	/**
	 * Populates the given {@link Collection sink} with converted values from the given {@link Collection source}.
	 *
	 * @param source the collection to create a {@link Collection} for, must not be {@literal null}.
	 * @param type the {@link TypeInformation} to consider or {@literal null} if unknown.
	 * @param sink the {@link Collection} to write to.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<Object> writeCollectionInternal(Collection<?> source, @Nullable TypeInformation<?> type,
			Collection<?> sink) {

		TypeInformation<?> componentType = null;

		List<Object> collection = sink instanceof List ? (List<Object>) sink : new ArrayList<>(sink);

		if (type != null) {
			componentType = type.getComponentType();
		}

		for (Object element : source) {

			Class<?> elementType = element == null ? null : element.getClass();

			if (elementType == null || conversions.isSimpleType(elementType)) {
				collection.add(getPotentiallyConvertedSimpleWrite(element));
			} else if (element instanceof Collection || elementType.isArray()) {
				collection.add(writeCollectionInternal(asCollection(element), componentType, new BasicDBList()));
			} else {
				Document document = new Document();
				writeInternal(element, document, componentType);
				collection.add(document);
			}
		}

		return collection;
	}

	/**
	 * Writes the given {@link Map} to the given {@link Document} considering the given {@link TypeInformation}.
	 *
	 * @param obj must not be {@literal null}.
	 * @param bson must not be {@literal null}.
	 * @param propertyType must not be {@literal null}.
	 * @return
	 */
	protected Bson writeMapInternal(Map<Object, Object> obj, Bson bson, TypeInformation<?> propertyType) {

		for (Map.Entry<Object, Object> entry : obj.entrySet()) {

			Object key = entry.getKey();
			Object val = entry.getValue();

			if (conversions.isSimpleType(key.getClass())) {

				String simpleKey = prepareMapKey(key);
				if (val == null || conversions.isSimpleType(val.getClass())) {
					writeSimpleInternal(val, bson, simpleKey);
				} else if (val instanceof Collection || val.getClass().isArray()) {
					addToMap(bson, simpleKey,
							writeCollectionInternal(asCollection(val), propertyType.getMapValueType(), new BasicDBList()));
				} else {
					Document document = new Document();
					TypeInformation<?> valueTypeInfo = propertyType.isMap() ? propertyType.getMapValueType()
							: ClassTypeInformation.OBJECT;
					writeInternal(val, document, valueTypeInfo);
					addToMap(bson, simpleKey, document);
				}
			} else {
				throw new MappingException("Cannot use a complex object as a key value.");
			}
		}

		return bson;
	}

	/**
	 * Prepares the given {@link Map} key to be converted into a {@link String}. Will invoke potentially registered custom
	 * conversions and escape dots from the result as they're not supported as {@link Map} key in MongoDB.
	 *
	 * @param key must not be {@literal null}.
	 * @return
	 */
	private String prepareMapKey(Object key) {

		Assert.notNull(key, "Map key must not be null!");

		String convertedKey = potentiallyConvertMapKey(key);
		return potentiallyEscapeMapKey(convertedKey);
	}

	/**
	 * Potentially replaces dots in the given map key with the configured map key replacement if configured or aborts
	 * conversion if none is configured.
	 *
	 * @see #setMapKeyDotReplacement(String)
	 * @param source
	 * @return
	 */
	protected String potentiallyEscapeMapKey(String source) {

		if (!source.contains(".")) {
			return source;
		}

		if (mapKeyDotReplacement == null) {
			throw new MappingException(String.format(
					"Map key %s contains dots but no replacement was configured! Make "
							+ "sure map keys don't contain dots in the first place or configure an appropriate replacement!",
					source));
		}

		return source.replaceAll("\\.", mapKeyDotReplacement);
	}

	/**
	 * Returns a {@link String} representation of the given {@link Map} key
	 *
	 * @param key
	 * @return
	 */
	private String potentiallyConvertMapKey(Object key) {

		if (key instanceof String) {
			return (String) key;
		}

		return conversions.hasCustomWriteTarget(key.getClass(), String.class)
				? (String) getPotentiallyConvertedSimpleWrite(key)
				: key.toString();
	}

	/**
	 * Translates the map key replacements in the given key just read with a dot in case a map key replacement has been
	 * configured.
	 *
	 * @param source
	 * @return
	 */
	protected String potentiallyUnescapeMapKey(String source) {
		return mapKeyDotReplacement == null ? source : source.replaceAll(mapKeyDotReplacement, "\\.");
	}

	/**
	 * Adds custom type information to the given {@link Document} if necessary. That is if the value is not the same as
	 * the one given. This is usually the case if you store a subtype of the actual declared type of the property.
	 *
	 * @param type
	 * @param value must not be {@literal null}.
	 * @param bson must not be {@literal null}.
	 */
	protected void addCustomTypeKeyIfNecessary(@Nullable TypeInformation<?> type, Object value, Bson bson) {

		Class<?> reference = type != null ? type.getActualType().getType() : Object.class;
		Class<?> valueType = ClassUtils.getUserClass(value.getClass());

		boolean notTheSameClass = !valueType.equals(reference);
		if (notTheSameClass) {
			typeMapper.writeType(valueType, bson);
		}
	}

	/**
	 * Writes the given simple value to the given {@link Document}. Will store enum names for enum values.
	 *
	 * @param value
	 * @param bson must not be {@literal null}.
	 * @param key must not be {@literal null}.
	 */
	private void writeSimpleInternal(Object value, Bson bson, String key) {
		addToMap(bson, key, getPotentiallyConvertedSimpleWrite(value));
	}

	private void writeSimpleInternal(Object value, Bson bson, MongoPersistentProperty property) {
		DocumentAccessor accessor = new DocumentAccessor(bson);
		accessor.put(property, getPotentiallyConvertedSimpleWrite(value));
	}

	/**
	 * Checks whether we have a custom conversion registered for the given value into an arbitrary simple Mongo type.
	 * Returns the converted value if so. If not, we perform special enum handling or simply return the value as is.
	 *
	 * @param value
	 * @return
	 */
	@Nullable
	private Object getPotentiallyConvertedSimpleWrite(@Nullable Object value) {

		if (value == null) {
			return null;
		}

		Optional<Class<?>> customTarget = conversions.getCustomWriteTarget(value.getClass());

		if (customTarget.isPresent()) {
			return conversionService.convert(value, customTarget.get());
		}

		if (ObjectUtils.isArray(value)) {

			if (value instanceof byte[]) {
				return value;
			}
			return asCollection(value);
		}

		return Enum.class.isAssignableFrom(value.getClass()) ? ((Enum<?>) value).name() : value;
	}

	/**
	 * Checks whether we have a custom conversion for the given simple object. Converts the given value if so, applies
	 * {@link Enum} handling or returns the value as is.
	 *
	 * @param value
	 * @param target must not be {@literal null}.
	 * @return
	 */
	@Nullable
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object getPotentiallyConvertedSimpleRead(@Nullable Object value, @Nullable Class<?> target) {

		if (value == null || target == null || ClassUtils.isAssignableValue(target, value)) {
			return value;
		}

		if (conversions.hasCustomReadTarget(value.getClass(), target)) {
			return conversionService.convert(value, target);
		}

		if (Enum.class.isAssignableFrom(target)) {
			return Enum.valueOf((Class<Enum>) target, value.toString());
		}

		return conversionService.convert(value, target);
	}

	protected DBRef createDBRef(Object target, MongoPersistentProperty property) {

		Assert.notNull(target, "Target object must not be null!");

		if (target instanceof DBRef) {
			return (DBRef) target;
		}

		MongoPersistentEntity<?> targetEntity = mappingContext.getPersistentEntity(target.getClass());
		targetEntity = targetEntity != null ? targetEntity : mappingContext.getPersistentEntity(property);

		if (null == targetEntity) {
			throw new MappingException("No mapping metadata found for " + target.getClass());
		}

		MongoPersistentEntity<?> entity = targetEntity;

		MongoPersistentProperty idProperty = entity.getIdProperty();

		if (idProperty != null) {

			Object id = target.getClass().equals(idProperty.getType()) ? target
					: entity.getPropertyAccessor(target).getProperty(idProperty);

			if (null == id) {
				throw new MappingException("Cannot create a reference to an object with a NULL id.");
			}

			return dbRefResolver.createDbRef(property == null ? null : property.getDBRef(), entity, idMapper.convertId(id));
		}

		throw new MappingException("No id property found on class " + entity.getType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.convert.ValueResolver#getValueInternal(org.springframework.data.mongodb.core.mapping.MongoPersistentProperty, com.mongodb.Document, org.springframework.data.mapping.model.SpELExpressionEvaluator, java.lang.Object)
	 */
	@Override
	public Object getValueInternal(MongoPersistentProperty prop, Bson bson, SpELExpressionEvaluator evaluator,
			ObjectPath path) {
		return new MongoDbPropertyValueProvider(bson, evaluator, path).getPropertyValue(prop);
	}

	/**
	 * Reads the given {@link BasicDBList} into a collection of the given {@link TypeInformation}.
	 *
	 * @param targetType must not be {@literal null}.
	 * @param source must not be {@literal null}.
	 * @param path must not be {@literal null}.
	 * @return the converted {@link Collection} or array, will never be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	private Object readCollectionOrArray(TypeInformation<?> targetType, Collection<?> source, ObjectPath path) {

		Assert.notNull(targetType, "Target type must not be null!");
		Assert.notNull(path, "Object path must not be null!");

		Class<?> collectionType = targetType.getType();
		collectionType = Collection.class.isAssignableFrom(collectionType) //
				? collectionType //
				: List.class;

		TypeInformation<?> componentType = targetType.getComponentType() != null //
				? targetType.getComponentType() //
				: ClassTypeInformation.OBJECT;
		Class<?> rawComponentType = componentType.getType();

		Collection<Object> items = targetType.getType().isArray() //
				? new ArrayList<>(source.size()) //
				: CollectionFactory.createCollection(collectionType, rawComponentType, source.size());

		if (source.isEmpty()) {
			return getPotentiallyConvertedSimpleRead(items, targetType.getType());
		}

		if (!DBRef.class.equals(rawComponentType) && isCollectionOfDbRefWhereBulkFetchIsPossible(source)) {

			List<Object> objects = bulkReadAndConvertDBRefs((List<DBRef>) source, componentType, path, rawComponentType);
			return getPotentiallyConvertedSimpleRead(objects, targetType.getType());
		}

		for (Object element : source) {

			if (element instanceof DBRef) {
				items.add(DBRef.class.equals(rawComponentType) ? element
						: readAndConvertDBRef((DBRef) element, componentType, path, rawComponentType));
			} else if (element instanceof Document) {
				items.add(read(componentType, (Document) element, path));
			} else if (element instanceof BasicDBObject) {
				items.add(read(componentType, (BasicDBObject) element, path));
			} else {

				if (!Object.class.equals(rawComponentType) && element instanceof Collection) {
					if (!rawComponentType.isArray() && !ClassUtils.isAssignable(Iterable.class, rawComponentType)) {
						throw new MappingException(
								String.format(INCOMPATIBLE_TYPES, element, element.getClass(), rawComponentType, path));
					}
				}
				if (element instanceof List) {
					items.add(readCollectionOrArray(componentType, (Collection<Object>) element, path));
				} else {
					items.add(getPotentiallyConvertedSimpleRead(element, rawComponentType));
				}
			}
		}

		return getPotentiallyConvertedSimpleRead(items, targetType.getType());
	}

	/**
	 * Reads the given {@link Document} into a {@link Map}. will recursively resolve nested {@link Map}s as well.
	 *
	 * @param type the {@link Map} {@link TypeInformation} to be used to unmarshall this {@link Document}.
	 * @param bson must not be {@literal null}
	 * @param path must not be {@literal null}
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Map<Object, Object> readMap(TypeInformation<?> type, Bson bson, ObjectPath path) {

		Assert.notNull(bson, "Document must not be null!");
		Assert.notNull(path, "Object path must not be null!");

		Class<?> mapType = typeMapper.readType(bson, type).getType();

		TypeInformation<?> keyType = type.getComponentType();
		TypeInformation<?> valueType = type.getMapValueType();

		Class<?> rawKeyType = keyType != null ? keyType.getType() : null;
		Class<?> rawValueType = valueType != null ? valueType.getType() : null;

		Map<String, Object> sourceMap = asMap(bson);
		Map<Object, Object> map = CollectionFactory.createMap(mapType, rawKeyType, sourceMap.keySet().size());

		if (!DBRef.class.equals(rawValueType) && isCollectionOfDbRefWhereBulkFetchIsPossible(sourceMap.values())) {
			bulkReadAndConvertDBRefMapIntoTarget(valueType, rawValueType, sourceMap, map);
			return map;
		}

		for (Entry<String, Object> entry : sourceMap.entrySet()) {

			if (typeMapper.isTypeKey(entry.getKey())) {
				continue;
			}

			Object key = potentiallyUnescapeMapKey(entry.getKey());

			if (rawKeyType != null && !rawKeyType.isAssignableFrom(key.getClass())) {
				key = conversionService.convert(key, rawKeyType);
			}

			Object value = entry.getValue();
			TypeInformation<?> defaultedValueType = valueType != null ? valueType : ClassTypeInformation.OBJECT;

			if (value instanceof Document) {
				map.put(key, read(defaultedValueType, (Document) value, path));
			} else if (value instanceof BasicDBObject) {
				map.put(key, read(defaultedValueType, (BasicDBObject) value, path));
			} else if (value instanceof DBRef) {
				map.put(key, DBRef.class.equals(rawValueType) ? value
						: readAndConvertDBRef((DBRef) value, defaultedValueType, ObjectPath.ROOT, rawValueType));
			} else if (value instanceof List) {
				map.put(key, readCollectionOrArray(valueType != null ? valueType : ClassTypeInformation.LIST,
						(List<Object>) value, path));
			} else {
				map.put(key, getPotentiallyConvertedSimpleRead(value, rawValueType));
			}
		}

		return map;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Bson bson) {

		if (bson instanceof Document) {
			return (Document) bson;
		}

		if (bson instanceof DBObject) {
			return ((DBObject) bson).toMap();
		}

		throw new IllegalArgumentException(
				String.format("Cannot read %s. as map. Given Bson must be a Document or DBObject!", bson.getClass()));
	}

	private static void addToMap(Bson bson, String key, @Nullable Object value) {

		if (bson instanceof Document) {
			((Document) bson).put(key, value);
			return;
		}
		if (bson instanceof DBObject) {
			((DBObject) bson).put(key, value);
			return;
		}
		throw new IllegalArgumentException(String.format(
				"Cannot add key/value pair to %s. as map. Given Bson must be a Document or DBObject!", bson.getClass()));
	}

	private static void addAllToMap(Bson bson, Map<String, ?> value) {

		if (bson instanceof Document) {
			((Document) bson).putAll(value);
			return;
		}

		if (bson instanceof DBObject) {
			((DBObject) bson).putAll(value);
			return;
		}

		throw new IllegalArgumentException(
				String.format("Cannot add all to %s. Given Bson must be a Document or DBObject.", bson.getClass()));
	}

	private static void removeFromMap(Bson bson, String key) {

		if (bson instanceof Document) {
			((Document) bson).remove(key);
			return;
		}

		if (bson instanceof DBObject) {
			((DBObject) bson).removeField(key);
			return;
		}

		throw new IllegalArgumentException(
				String.format("Cannot remove from %s. Given Bson must be a Document or DBObject.", bson.getClass()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.convert.MongoWriter#convertToMongoType(java.lang.Object, org.springframework.data.util.TypeInformation)
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	@Override
	public Object convertToMongoType(@Nullable Object obj, TypeInformation<?> typeInformation) {

		if (obj == null) {
			return null;
		}

		Optional<Class<?>> target = conversions.getCustomWriteTarget(obj.getClass());
		if (target.isPresent()) {
			return conversionService.convert(obj, target.get());
		}

		if (conversions.isSimpleType(obj.getClass())) {
			// Doesn't need conversion
			return getPotentiallyConvertedSimpleWrite(obj);
		}

		if (obj instanceof List) {
			return maybeConvertList((List<Object>) obj, typeInformation);
		}

		if (obj instanceof Document) {

			Document newValueDocument = new Document();
			for (String vk : ((Document) obj).keySet()) {
				Object o = ((Document) obj).get(vk);
				newValueDocument.put(vk, convertToMongoType(o, typeInformation));
			}
			return newValueDocument;
		}

		if (obj instanceof DBObject) {

			Document newValueDbo = new Document();
			for (String vk : ((DBObject) obj).keySet()) {

				Object o = ((DBObject) obj).get(vk);
				newValueDbo.put(vk, convertToMongoType(o, typeInformation));
			}

			return newValueDbo;
		}

		if (obj instanceof Map) {

			Document result = new Document();

			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
				result.put(entry.getKey().toString(), convertToMongoType(entry.getValue(), typeInformation));
			}

			return result;
		}

		if (obj.getClass().isArray()) {
			return maybeConvertList(Arrays.asList((Object[]) obj), typeInformation);
		}

		if (obj instanceof Collection) {
			return maybeConvertList((Collection<?>) obj, typeInformation);
		}

		Document newDocument = new Document();
		this.write(obj, newDocument);

		if (typeInformation == null) {
			return removeTypeInfo(newDocument, true);
		}

		if (typeInformation.getType().equals(NestedDocument.class)) {
			return removeTypeInfo(newDocument, false);
		}

		return !obj.getClass().equals(typeInformation.getType()) ? newDocument : removeTypeInfo(newDocument, true);
	}

	public List<Object> maybeConvertList(Iterable<?> source, TypeInformation<?> typeInformation) {

		List<Object> newDbl = new ArrayList<>();

		for (Object element : source) {
			newDbl.add(convertToMongoType(element, typeInformation));
		}

		return newDbl;
	}

	/**
	 * Removes the type information from the entire conversion result.
	 *
	 * @param object
	 * @param recursively whether to apply the removal recursively
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Object removeTypeInfo(Object object, boolean recursively) {

		if (!(object instanceof Document)) {
			return object;
		}

		Document document = (Document) object;
		String keyToRemove = null;

		for (String key : document.keySet()) {

			if (recursively) {

				Object value = document.get(key);

				if (value instanceof BasicDBList) {
					for (Object element : (BasicDBList) value) {
						removeTypeInfo(element, recursively);
					}
				} else if (value instanceof List) {
					for (Object element : (List<Object>) value) {
						removeTypeInfo(element, recursively);
					}
				} else {
					removeTypeInfo(value, recursively);
				}
			}

			if (typeMapper.isTypeKey(key)) {

				keyToRemove = key;

				if (!recursively) {
					break;
				}
			}
		}

		if (keyToRemove != null) {
			document.remove(keyToRemove);
		}

		return document;
	}

	/**
	 * {@link PropertyValueProvider} to evaluate a SpEL expression if present on the property or simply accesses the field
	 * of the configured source {@link Document}.
	 *
	 * @author Oliver Gierke
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 */
	class MongoDbPropertyValueProvider implements PropertyValueProvider<MongoPersistentProperty> {

		final DocumentAccessor accessor;
		final SpELExpressionEvaluator evaluator;
		final ObjectPath path;

		/**
		 * Creates a new {@link MongoDbPropertyValueProvider} for the given source, {@link SpELExpressionEvaluator} and
		 * {@link ObjectPath}.
		 *
		 * @param source must not be {@literal null}.
		 * @param evaluator must not be {@literal null}.
		 * @param path must not be {@literal null}.
		 */
		MongoDbPropertyValueProvider(Bson source, SpELExpressionEvaluator evaluator, ObjectPath path) {
			this(new DocumentAccessor(source), evaluator, path);
		}

		/**
		 * Creates a new {@link MongoDbPropertyValueProvider} for the given source, {@link SpELExpressionEvaluator} and
		 * {@link ObjectPath}.
		 *
		 * @param accessor must not be {@literal null}.
		 * @param evaluator must not be {@literal null}.
		 * @param path must not be {@literal null}.
		 */
		MongoDbPropertyValueProvider(DocumentAccessor accessor, SpELExpressionEvaluator evaluator, ObjectPath path) {

			Assert.notNull(accessor, "DocumentAccessor must no be null!");
			Assert.notNull(evaluator, "SpELExpressionEvaluator must not be null!");
			Assert.notNull(path, "ObjectPath must not be null!");

			this.accessor = accessor;
			this.evaluator = evaluator;
			this.path = path;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.convert.PropertyValueProvider#getPropertyValue(org.springframework.data.mapping.PersistentProperty)
		 */
		@Nullable
		public <T> T getPropertyValue(MongoPersistentProperty property) {

			String expression = property.getSpelExpression();
			Object value = expression != null ? evaluator.evaluate(expression) : accessor.get(property);

			if (value == null) {
				return null;
			}

			return readValue(value, property.getTypeInformation(), path);
		}
	}

	/**
	 * {@link PropertyValueProvider} that is aware of {@link MongoPersistentProperty#isAssociation()} and that delegates
	 * resolution to {@link DbRefResolver}.
	 *
	 * @author Mark Paluch
	 * @author Christoph Strobl
	 * @since 2.1
	 */
	class AssociationAwareMongoDbPropertyValueProvider extends MongoDbPropertyValueProvider {

		/**
		 * Creates a new {@link AssociationAwareMongoDbPropertyValueProvider} for the given source,
		 * {@link SpELExpressionEvaluator} and {@link ObjectPath}.
		 *
		 * @param source must not be {@literal null}.
		 * @param evaluator must not be {@literal null}.
		 * @param path must not be {@literal null}.
		 */
		AssociationAwareMongoDbPropertyValueProvider(Bson source, SpELExpressionEvaluator evaluator, ObjectPath path) {
			super(source, evaluator, path);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.convert.PropertyValueProvider#getPropertyValue(org.springframework.data.mapping.PersistentProperty)
		 */
		@Nullable
		@SuppressWarnings("unchecked")
		public <T> T getPropertyValue(MongoPersistentProperty property) {

			if (property.isDbReference() && property.getDBRef().lazy()) {

				Object rawRefValue = accessor.get(property);
				if (rawRefValue == null) {
					return null;
				}

				DbRefResolverCallback callback = new DefaultDbRefResolverCallback(accessor.getDocument(), path, evaluator,
						MappingMongoConverter.this);

				DBRef dbref = rawRefValue instanceof DBRef ? (DBRef) rawRefValue : null;
				return (T) dbRefResolver.resolveDbRef(property, dbref, callback, dbRefProxyHandler);
			}

			return super.getPropertyValue(property);
		}
	}

	/**
	 * Extension of {@link SpELExpressionParameterValueProvider} to recursively trigger value conversion on the raw
	 * resolved SpEL value.
	 *
	 * @author Oliver Gierke
	 */
	private class ConverterAwareSpELExpressionParameterValueProvider
			extends SpELExpressionParameterValueProvider<MongoPersistentProperty> {

		private final ObjectPath path;

		/**
		 * Creates a new {@link ConverterAwareSpELExpressionParameterValueProvider}.
		 *
		 * @param evaluator must not be {@literal null}.
		 * @param conversionService must not be {@literal null}.
		 * @param delegate must not be {@literal null}.
		 */
		public ConverterAwareSpELExpressionParameterValueProvider(SpELExpressionEvaluator evaluator,
				ConversionService conversionService, ParameterValueProvider<MongoPersistentProperty> delegate,
				ObjectPath path) {

			super(evaluator, conversionService, delegate);
			this.path = path;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mapping.model.SpELExpressionParameterValueProvider#potentiallyConvertSpelValue(java.lang.Object, org.springframework.data.mapping.PreferredConstructor.Parameter)
		 */
		@Override
		protected <T> T potentiallyConvertSpelValue(Object object, Parameter<T, MongoPersistentProperty> parameter) {
			return readValue(object, parameter.getType(), path);
		}
	}

	@Nullable
	@SuppressWarnings("unchecked")
	<T> T readValue(Object value, TypeInformation<?> type, ObjectPath path) {

		Class<?> rawType = type.getType();

		if (conversions.hasCustomReadTarget(value.getClass(), rawType)) {
			return (T) conversionService.convert(value, rawType);
		} else if (value instanceof DBRef) {
			return potentiallyReadOrResolveDbRef((DBRef) value, type, path, rawType);
		} else if (value instanceof List) {
			return (T) readCollectionOrArray(type, (List<Object>) value, path);
		} else if (value instanceof Document) {
			return (T) read(type, (Document) value, path);
		} else if (value instanceof DBObject) {
			return (T) read(type, (BasicDBObject) value, path);
		} else {
			return (T) getPotentiallyConvertedSimpleRead(value, rawType);
		}
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private <T> T potentiallyReadOrResolveDbRef(@Nullable DBRef dbref, TypeInformation<?> type, ObjectPath path,
			Class<?> rawType) {

		if (rawType.equals(DBRef.class)) {
			return (T) dbref;
		}

		T object = dbref == null ? null : path.getPathItem(dbref.getId(), dbref.getCollectionName(), (Class<T>) rawType);
		return object != null ? object : readAndConvertDBRef(dbref, type, path, rawType);
	}

	@Nullable
	private <T> T readAndConvertDBRef(@Nullable DBRef dbref, TypeInformation<?> type, ObjectPath path,
			final Class<?> rawType) {

		List<T> result = bulkReadAndConvertDBRefs(Collections.singletonList(dbref), type, path, rawType);
		return CollectionUtils.isEmpty(result) ? null : result.iterator().next();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void bulkReadAndConvertDBRefMapIntoTarget(TypeInformation<?> valueType, Class<?> rawValueType,
			Map<String, Object> sourceMap, Map<Object, Object> targetMap) {

		LinkedHashMap<String, Object> referenceMap = new LinkedHashMap<>(sourceMap);
		List<Object> convertedObjects = bulkReadAndConvertDBRefs((List<DBRef>) new ArrayList(referenceMap.values()),
				valueType, ObjectPath.ROOT, rawValueType);
		int index = 0;

		for (String key : referenceMap.keySet()) {
			targetMap.put(key, convertedObjects.get(index));
			index++;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> bulkReadAndConvertDBRefs(List<DBRef> dbrefs, TypeInformation<?> type, ObjectPath path,
			final Class<?> rawType) {

		if (CollectionUtils.isEmpty(dbrefs)) {
			return Collections.emptyList();
		}

		List<Document> referencedRawDocuments = dbrefs.size() == 1
				? Collections.singletonList(readRef(dbrefs.iterator().next()))
				: bulkReadRefs(dbrefs);
		String collectionName = dbrefs.iterator().next().getCollectionName();

		List<T> targeList = new ArrayList<>(dbrefs.size());

		for (Document document : referencedRawDocuments) {

			if (document != null) {
				maybeEmitEvent(new AfterLoadEvent<>(document, (Class<T>) rawType, collectionName));
			}

			final T target = (T) read(type, document, path);
			targeList.add(target);

			if (target != null) {
				maybeEmitEvent(new AfterConvertEvent<>(document, target, collectionName));
			}
		}

		return targeList;
	}

	private void maybeEmitEvent(MongoMappingEvent<?> event) {

		if (canPublishEvent()) {
			this.applicationContext.publishEvent(event);
		}
	}

	private boolean canPublishEvent() {
		return this.applicationContext != null;
	}

	/**
	 * Performs the fetch operation for the given {@link DBRef}.
	 *
	 * @param ref
	 * @return
	 */
	Document readRef(DBRef ref) {
		return dbRefResolver.fetch(ref);
	}

	/**
	 * Performs a bulk fetch operation for the given {@link DBRef}s.
	 *
	 * @param references must not be {@literal null}.
	 * @return never {@literal null}.
	 * @since 1.10
	 */
	List<Document> bulkReadRefs(List<DBRef> references) {
		return dbRefResolver.bulkFetch(references);
	}

	/**
	 * Returns whether the given {@link Iterable} contains {@link DBRef} instances all pointing to the same collection.
	 *
	 * @param source must not be {@literal null}.
	 * @return
	 */
	private static boolean isCollectionOfDbRefWhereBulkFetchIsPossible(Iterable<?> source) {

		Assert.notNull(source, "Iterable of DBRefs must not be null!");

		Set<String> collectionsFound = new HashSet<>();

		for (Object dbObjItem : source) {

			if (!(dbObjItem instanceof DBRef)) {
				return false;
			}

			collectionsFound.add(((DBRef) dbObjItem).getCollectionName());

			if (collectionsFound.size() > 1) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Marker class used to indicate we have a non root document object here that might be used within an update - so we
	 * need to preserve type hints for potential nested elements but need to remove it on top level.
	 *
	 * @author Christoph Strobl
	 * @since 1.8
	 */
	static class NestedDocument {

	}
}
