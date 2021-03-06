/**
 * Copyright 2016 Hortonworks.
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
 **/
package com.hortonworks.registries.schemaregistry.avro;

import com.hortonworks.registries.serdes.Device;
import com.hortonworks.registries.common.test.IntegrationTest;
import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroDeserializer;
import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 *
 */
@Category(IntegrationTest.class)
public class KafkaAvroSerDesTest extends AbstractAvroSchemaRegistryCientTest {

    @Test
    public void testPrimitiveSerDes() {
        String topicPrefix = TEST_NAME_RULE.getMethodName() + "-" + System.currentTimeMillis();

        _testPrimitiveSerDes(topicPrefix);
    }

    private void _testPrimitiveSerDes(String topicPrefix) {
        Object[] payloads = generatePrimitivePayloads();

        for (Object payload : payloads) {
            String topic = topicPrefix + ":" + (payload != null ? payload.getClass().getName() : "null");
            _testKafkaSerDes(topic, true, payload);
            _testKafkaSerDes(topic, false, payload);
        }
    }

    private void _testKafkaSerDes(String topic, boolean isKey, Object payload) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(SCHEMA_REGISTRY_CLIENT_CONF, isKey);
        KafkaAvroDeserializer avroDeserializer = new KafkaAvroDeserializer();
        avroDeserializer.configure(SCHEMA_REGISTRY_CLIENT_CONF, isKey);

        byte[] serializedData = avroSerializer.serialize(topic, payload);
        Object deserializedObj = avroDeserializer.deserialize(topic, serializedData);
        if (payload instanceof byte[]) {
            Assert.assertArrayEquals((byte[]) payload, (byte[]) deserializedObj);
        } else {
            assertAvroObjs(payload, deserializedObj);
        }
    }

    @Test
    public void testAvroRecordsSerDes() throws Exception {
        String topicPrefix = TEST_NAME_RULE.getMethodName() + "-" + System.currentTimeMillis();

        String genericRecordTopic = topicPrefix + "-generic";
        Object genericRecordForDevice = createGenericRecordForDevice();
        _testKafkaSerDes(genericRecordTopic, true, genericRecordForDevice);
        _testKafkaSerDes(genericRecordTopic, true, genericRecordForDevice);

        Device specificRecord = createSpecificRecord();
        String specificRecordTopic = topicPrefix + "-specific";
        _testKafkaSerDes(specificRecordTopic, true, specificRecord);
        _testKafkaSerDes(specificRecordTopic, false, specificRecord);
    }

    @Test
    public void testIncompatibleSchemas() throws Exception {
        String topic = TEST_NAME_RULE.getMethodName() + "-" + System.currentTimeMillis();

        // send initial message
        Object initialMsg = createGenericRecordForDevice();
        _testKafkaSerDes(topic, true, initialMsg);
        _testKafkaSerDes(topic, false, initialMsg);

        // send a message with incompatible version of the schema
        Object incompatMsg = createGenericRecordForIncompatDevice();
        try {
            _testKafkaSerDes(topic, true, incompatMsg);
            Assert.fail("An error should have been received here because of incompatible schemas");
        } catch (Exception e) {
            // should have received an error.
        }

        // send a message with compatible version of the schema
        Object compatMsg = createGenericRecordForCompatDevice();
        _testKafkaSerDes(topic, true, compatMsg);
        _testKafkaSerDes(topic, false, compatMsg);
    }

}
