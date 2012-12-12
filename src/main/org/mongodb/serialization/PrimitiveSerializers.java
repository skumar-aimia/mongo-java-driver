/**
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.mongodb.serialization;

import org.bson.BSONReader;
import org.bson.BSONWriter;
import org.bson.BsonType;
import org.mongodb.MongoException;
import org.mongodb.serialization.serializers.BinarySerializer;
import org.mongodb.serialization.serializers.BooleanSerializer;
import org.mongodb.serialization.serializers.DateSerializer;
import org.mongodb.serialization.serializers.DoubleSerializer;
import org.mongodb.serialization.serializers.IntegerSerializer;
import org.mongodb.serialization.serializers.LongSerializer;
import org.mongodb.serialization.serializers.NullSerializer;
import org.mongodb.serialization.serializers.ObjectIdSerializer;
import org.mongodb.serialization.serializers.PatternSerializer;
import org.mongodb.serialization.serializers.StringSerializer;

import java.util.HashMap;
import java.util.Map;

// TODO: this is getting better, but still not sure

/**
 * Holder for all the serializer mappings.
 */
public class PrimitiveSerializers implements Serializer<Object> {
    private Map<Class, Serializer> classSerializerMap = new HashMap<Class, Serializer>();
    private Map<BsonType, Serializer> bsonTypeSerializerMap = new HashMap<BsonType, Serializer>();

    private PrimitiveSerializers(final Map<Class, Serializer> classSerializerMap, final Map<BsonType, Serializer> bsonTypeSerializerMap) {
        this.classSerializerMap = classSerializerMap;
        this.bsonTypeSerializerMap = bsonTypeSerializerMap;
    }

    @Override
    public void serialize(final BSONWriter writer, final Object value,
                          final BsonSerializationOptions options) {
        Serializer serializer = classSerializerMap.get(value.getClass());
        if (serializer == null) {
            throw new MongoException("No serializer for class " + value.getClass().getName());
        }
        serializer.serialize(writer, value, options);  // TODO: unchecked call
    }

    @Override
    public Object deserialize(final BSONReader reader, final BsonSerializationOptions options) {
        BsonType bsonType = reader.getCurrentBsonType();
        Serializer serializer = bsonTypeSerializerMap.get(bsonType);
        if (serializer == null) {
            throw new MongoException("Unable to find deserializer for BSON type " + bsonType);
        }
        return serializer.deserialize(reader, options);
    }

    @Override
    public Class<Object> getSerializationClass() {
        return Object.class;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(PrimitiveSerializers base) {
        return new Builder(base);
    }

    // TODO: find a proper way to do this...
    public static PrimitiveSerializers createDefault() {
        return builder()
        .objectIdSerializer(new ObjectIdSerializer())
        .integerSerializer(new IntegerSerializer())
        .longSerializer(new LongSerializer())
        .stringSerializer(new StringSerializer())
        .doubleSerializer(new DoubleSerializer())
        .binarySerializer(new BinarySerializer())
        .dateSerializer(new DateSerializer())
        .booleanSerializer(new BooleanSerializer())
        .patternSerializer(new PatternSerializer())
        .nullSerializer(new NullSerializer())
        .build();
    }


    public static class Builder {
        private Map<Class, Serializer> classSerializerMap = new HashMap<Class, Serializer>();
        private Map<BsonType, Serializer> bsonTypeSerializerMap = new HashMap<BsonType, Serializer>();

        public Builder() {
        }

        public Builder(final PrimitiveSerializers base) {
            classSerializerMap.putAll(base.classSerializerMap);
            bsonTypeSerializerMap.putAll(base.bsonTypeSerializerMap);
        }


        public Builder objectIdSerializer(Serializer serializer) {
            registerSerializer(BsonType.OBJECT_ID, serializer);
            return this;
        }

        public Builder integerSerializer(Serializer serializer) {
            registerSerializer(BsonType.INT32, serializer);
            return this;
        }

        public Builder longSerializer(Serializer serializer) {
            registerSerializer(BsonType.INT64, serializer);
            return this;
        }

        public Builder stringSerializer(Serializer serializer) {
            registerSerializer(BsonType.STRING, serializer);
            return this;
        }

        public Builder doubleSerializer(Serializer serializer) {
            registerSerializer(BsonType.DOUBLE, serializer);
            return this;
        }

        public Builder binarySerializer(Serializer serializer) {
            registerSerializer(BsonType.BINARY, serializer);
            return this;
        }

        public Builder dateSerializer(Serializer serializer) {
            registerSerializer(BsonType.DATE_TIME, serializer);
            return this;
        }

        public Builder booleanSerializer(Serializer serializer) {
            registerSerializer(BsonType.BOOLEAN, serializer);
            return this;
        }

        public Builder patternSerializer(Serializer serializer) {
            registerSerializer(BsonType.REGULAR_EXPRESSION, serializer);
            return this;
        }

        public Builder nullSerializer(Serializer serializer) {
            registerSerializer(BsonType.NULL, serializer);
            return this;
        }

        public PrimitiveSerializers build() {
            return new PrimitiveSerializers(classSerializerMap, bsonTypeSerializerMap);
        }


        private void registerSerializer(final BsonType bsonType, final Serializer serializer) {
            bsonTypeSerializerMap.put(bsonType, serializer);
            classSerializerMap.put(serializer.getSerializationClass(), serializer);
        }
    }
}
