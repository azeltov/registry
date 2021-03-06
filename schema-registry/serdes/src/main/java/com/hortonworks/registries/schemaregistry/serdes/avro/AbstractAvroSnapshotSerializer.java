/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.registries.schemaregistry.serdes.avro;

import com.hortonworks.registries.schemaregistry.SchemaIdVersion;
import com.hortonworks.registries.schemaregistry.client.ISchemaRegistryClient;
import com.hortonworks.registries.schemaregistry.serde.AbstractSnapshotDeserializer;
import com.hortonworks.registries.schemaregistry.serde.AbstractSnapshotSerializer;
import com.hortonworks.registries.schemaregistry.serde.SnapshotDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * The below example describes how to extend this serializer with user supplied representation like MessageContext class.
 * Respective {@link SnapshotDeserializer} implementation is done extending {@link AbstractSnapshotDeserializer}.
 *
 * <pre>{@code
    public class MessageContext {
        final Map<String, Object> headers;
        final InputStream payloadEntity;

        public MessageContext(Map<String, Object> headers, InputStream payloadEntity) {
            this.headers = headers;
            this.payloadEntity = payloadEntity;
        }
    }

    public class MessageContextBasedAvroSerializer extends AbstractAvroSnapshotSerializer<MessageContext> {

        {@literal @}Override
        protected MessageContext doSerialize(Object input, SchemaIdVersion schemaIdVersion) throws SerDesException {
            Map<String, Object> headers = new HashMap<>();

            headers.put("protocol.id", 0x1);
            headers.put("schema.metadata.id", schemaIdVersion.getSchemaMetadataId());
            headers.put("schema.version", schemaIdVersion.getVersion());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try(BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(baos)) {
                writeContentPayload(input, bufferedOutputStream);
            } catch (IOException e) {
                throw new SerDesException(e);
            }

            ByteArrayInputStream payload = new ByteArrayInputStream(baos.toByteArray());

            return new MessageContext(headers, payload);
        }
    }
  }</pre>
 *
 * @param <O> serialized output type. For ex: byte[], String etc.
 */
public abstract class AbstractAvroSnapshotSerializer<O> extends AbstractSnapshotSerializer<Object, O> {

    public AbstractAvroSnapshotSerializer() {
        super();
    }

    public AbstractAvroSnapshotSerializer(ISchemaRegistryClient schemaRegistryClient) {
        super(schemaRegistryClient);
    }
    
    /**
     * Writes given {@code input} avro object in a binary format into given {@code outputStream}
     *
     * @param input avro object
     * @param outputStream into which binary form of {@code input} is written.
     * @throws IOException when any IO error occurs
     */
    protected void writeContentPayload(Object input, OutputStream outputStream) throws IOException {
        Schema schema = computeSchema(input);
        Schema.Type schemaType = schema.getType();
        if (Schema.Type.BYTES.equals(schemaType)) {
            // incase of byte arrays, no need to go through avro as there is not much to optimize and avro is expecting
            // the payload to be ByteBuffer instead of a byte array
            outputStream.write((byte[]) input);
        } else if (Schema.Type.STRING.equals(schemaType)) {
            // get UTF-8 bytes and directly send those over instead of using avro.
            outputStream.write(input.toString().getBytes(AvroUtils.UTF_8));
        } else {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            DatumWriter<Object> writer;
            boolean isSpecificRecord = input instanceof SpecificRecord;
            if (isSpecificRecord) {
                writer = new SpecificDatumWriter<>(schema);
            } else {
                writer = new GenericDatumWriter<>(schema);
            }

            writer.write(input, encoder);
            encoder.flush();
        }
    }

    /**
     * Writes given {@code protocolId} into {@code outputStream}
     *
     * @param protocolId
     * @param outputStream
     * @throws IOException when any IO error occurs
     */
    protected void writeProtocolId(byte protocolId,
                                   OutputStream outputStream) throws IOException {
        // it can be enhanced to have respective protocol handlers for different versions
        // first byte is protocol version/id.
        // protocol format:
        // 1 byte  : protocol version
        outputStream.write(new byte[]{protocolId});
    }

    /**
     * Writes given {@code schemaIdVersion} into {@code outputStream}
     *
     * @param schemaIdVersion
     * @param byteArrayOutputStream
     * @throws IOException when any IO error occurs
     */
    protected void writeSchemaVersion(SchemaIdVersion schemaIdVersion,
                                      OutputStream byteArrayOutputStream) throws IOException {
        // 8 bytes : schema metadata Id
        // 4 bytes : schema version
        byteArrayOutputStream.write(ByteBuffer.allocate(12)
                                            .putLong(schemaIdVersion.getSchemaMetadataId())
                                            .putInt(schemaIdVersion.getVersion()).array());
    }

    /**
     * @param input avro object
     * @return textual representation of the schema of the given {@code input} avro object
     */
    protected String getSchemaText(Object input) {
        Schema schema = computeSchema(input);
        return schema.toString();
    }

    private Schema computeSchema(Object input) {
        Schema schema = null;
        if (input instanceof GenericContainer) {
            schema = ((GenericContainer) input).getSchema();
        } else {
            schema = AvroUtils.getSchemaForPrimitives(input);
        }
        return schema;
    }
}
