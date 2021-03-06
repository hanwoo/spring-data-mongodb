/*
 * Copyright (c) 2011 by the original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.query;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import java.math.BigInteger;
import java.util.Arrays;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.Person;
import org.springframework.data.mongodb.core.QueryMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

/**
 * Unit tests for {@link QueryMapper}.
 * 
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unused")
public class QueryMapperUnitTests {

	QueryMapper mapper;
	MongoMappingContext context;

	@Mock
	MongoDbFactory factory;

	@Before
	public void setUp() {

		context = new MongoMappingContext();

		MappingMongoConverter converter = new MappingMongoConverter(factory, context);
		converter.afterPropertiesSet();

		mapper = new QueryMapper(converter);
	}

	@Test
	public void translatesIdPropertyIntoIdKey() {

		DBObject query = new BasicDBObject("foo", "value");
		MongoPersistentEntity<?> entity = context.getPersistentEntity(Sample.class);

		DBObject result = mapper.getMappedObject(query, entity);
		assertThat(result.get("_id"), is(notNullValue()));
		assertThat(result.get("foo"), is(nullValue()));
	}

	@Test
	public void convertsStringIntoObjectId() {

		DBObject query = new BasicDBObject("_id", new ObjectId().toString());
		DBObject result = mapper.getMappedObject(query, null);
		assertThat(result.get("_id"), is(ObjectId.class));
	}

	@Test
	public void handlesBigIntegerIdsCorrectly() {

		DBObject dbObject = new BasicDBObject("id", new BigInteger("1"));
		DBObject result = mapper.getMappedObject(dbObject, null);
		assertThat(result.get("_id"), is((Object) "1"));
	}

	@Test
	public void handlesObjectIdCapableBigIntegerIdsCorrectly() {

		ObjectId id = new ObjectId();
		DBObject dbObject = new BasicDBObject("id", new BigInteger(id.toString(), 16));
		DBObject result = mapper.getMappedObject(dbObject, null);
		assertThat(result.get("_id"), is((Object) id));
	}

	/**
	 * @see DATAMONGO-278
	 */
	@Test
	public void translates$NeCorrectly() {

		Criteria criteria = where("foo").ne(new ObjectId().toString());

		DBObject result = mapper.getMappedObject(criteria.getCriteriaObject(), context.getPersistentEntity(Sample.class));
		Object object = result.get("_id");
		assertThat(object, is(DBObject.class));
		DBObject dbObject = (DBObject) object;
		assertThat(dbObject.get("$ne"), is(ObjectId.class));
	}

	/**
	 * @see DATAMONGO-326
	 */
	@Test
	public void handlesEnumsCorrectly() {
		Query query = query(where("foo").is(Enum.INSTANCE));
		DBObject result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(String.class));
	}

	@Test
	public void handlesEnumsInNotEqualCorrectly() {
		Query query = query(where("foo").ne(Enum.INSTANCE));
		DBObject result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(DBObject.class));

		Object ne = ((DBObject) object).get("$ne");
		assertThat(ne, is(String.class));
		assertThat(ne.toString(), is(Enum.INSTANCE.name()));
	}

	@Test
	public void handlesEnumsIn$InCorrectly() {

		Query query = query(where("foo").in(Enum.INSTANCE));
		DBObject result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(DBObject.class));

		Object in = ((DBObject) object).get("$in");
		assertThat(in, is(BasicDBList.class));

		BasicDBList list = (BasicDBList) in;
		assertThat(list.size(), is(1));
		assertThat(list.get(0), is(String.class));
		assertThat(list.get(0).toString(), is(Enum.INSTANCE.name()));
	}

	/**
	 * @see DATAMONGO-373
	 */
	@Test
	public void handlesNativelyBuiltQueryCorrectly() {

		DBObject query = new QueryBuilder().or(new BasicDBObject("foo", "bar")).get();
		mapper.getMappedObject(query, null);
	}

	/**
	 * @see DATAMONGO-369
	 */
	@Test
	public void handlesAllPropertiesIfDBObject() {

		DBObject query = new BasicDBObject();
		query.put("foo", new BasicDBObject("$in", Arrays.asList(1, 2)));
		query.put("bar", new Person());

		DBObject result = mapper.getMappedObject(query, null);
		assertThat(result.get("bar"), is(notNullValue()));
	}

	class Sample {

		@Id
		private String foo;
	}

	class BigIntegerId {

		@Id
		private BigInteger id;
	}

	enum Enum {
		INSTANCE;
	}
}
