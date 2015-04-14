package com.ctrip.hermes.meta.service;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema.Parser;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.unidal.dal.jdbc.DalException;
import org.unidal.lookup.annotation.Inject;
import org.unidal.lookup.annotation.Named;

import com.ctrip.hermes.core.meta.MetaManager;
import com.ctrip.hermes.core.meta.MetaService;
import com.ctrip.hermes.meta.dal.meta.Schema;
import com.ctrip.hermes.meta.dal.meta.SchemaDao;
import com.ctrip.hermes.meta.dal.meta.SchemaEntity;
import com.ctrip.hermes.meta.entity.Topic;
import com.ctrip.hermes.meta.pojo.SchemaView;
import com.google.common.io.ByteStreams;

@Named
public class SchemaService {

	private SchemaRegistryClient avroSchemaRegistry = new CachedSchemaRegistryClient("http://10.3.8.63:8081", 1000);

	@Inject
	private SchemaDao schemaDao;

	@Inject(ServerMetaManager.ID)
	private MetaManager m_metaManager;

	@Inject
	private MetaService m_metaService;

	@Inject
	private TopicService m_topicService;

	public void createAvroSchema(String schemaName, org.apache.avro.Schema avroSchema) throws IOException,
	      RestClientException, DalException {
		Schema schema = schemaDao.findLatestByName(schemaName, SchemaEntity.READSET_FULL);
		int avroid = this.avroSchemaRegistry.register(schemaName, avroSchema);
		schema.setAvroid(avroid);
		schemaDao.updateByPK(schema, SchemaEntity.UPDATESET_FULL);
	}

	public SchemaView createSchema(SchemaView schemaView) throws DalException {
		Schema schema = schemaView.toMetaSchema();
		schema.setCreateTime(new Date(System.currentTimeMillis()));
		schema.setVersion(1);
		schemaDao.insert(schema);

		Topic topic = m_metaService.findTopic(schemaView.getTopicId());
		topic.setSchemaId(schema.getId());
		m_topicService.updateTopic(topic);

		return new SchemaView(schema);
	}

	public void deleteSchema(String name) throws DalException {
		Schema schema = getSchemaMeta(name);
		schemaDao.deleteByPK(schema);
	}

	public org.apache.avro.Schema getAvroSchema(String schemaName) throws IOException, RestClientException, DalException {
		Schema schema = schemaDao.findLatestByName(schemaName, SchemaEntity.READSET_FULL);
		if (schema.getAvroid() > 0) {
			org.apache.avro.Schema avroSchema = this.avroSchemaRegistry.getByID(schema.getAvroid());
			return avroSchema;
		}
		return null;
	}

	public Schema getSchemaMeta(String schemaName) throws DalException {
		Schema schema = schemaDao.findLatestByName(schemaName, SchemaEntity.READSET_FULL);
		return schema;
	}

	public Schema getSchemaMeta(long schemaId) throws DalException {
		Schema schema = schemaDao.findByPK(schemaId, SchemaEntity.READSET_FULL);
		return schema;
	}

	public SchemaView getSchemaView(String schemaName) throws DalException, IOException, RestClientException {
		Schema schema = getSchemaMeta(schemaName);
		SchemaView schemaView = new SchemaView(schema);
		if (schema.getAvroid() > 0) {
			SchemaMetadata avroSchemaMeta = this.avroSchemaRegistry.getLatestSchemaMetadata(schema.getName());
			Map<String, Object> config = new HashMap<>();
			config.put("avro.schema", avroSchemaMeta.getSchema());
			config.put("avro.id", avroSchemaMeta.getId());
			config.put("avro.version", avroSchemaMeta.getVersion());
			schemaView.setConfig(config);
		}
		return schemaView;
	}

	public SchemaView getSchemaView(long schemaId) throws DalException, IOException, RestClientException {
		Schema schema = getSchemaMeta(schemaId);
		SchemaView schemaView = new SchemaView(schema);
		if (schema.getAvroid() > 0) {
			SchemaMetadata avroSchemaMeta = this.avroSchemaRegistry.getLatestSchemaMetadata(schema.getName());
			Map<String, Object> config = new HashMap<>();
			config.put("avro.schema", avroSchemaMeta.getSchema());
			config.put("avro.id", avroSchemaMeta.getId());
			config.put("avro.version", avroSchemaMeta.getVersion());
			schemaView.setConfig(config);
		}
		return schemaView;
	}

	public SchemaView updateSchemaView(SchemaView schemaView) throws DalException {
		Schema schema = schemaView.toMetaSchema();
		Schema oldSchema = schemaDao.findLatestByName(schema.getName(), SchemaEntity.READSET_FULL);
		schema.setVersion(oldSchema.getVersion() + 1);
		schema.setCreateTime(new Date(System.currentTimeMillis()));
		schema.setId(0);
		schemaDao.insert(schema);
		return new SchemaView(schema);
	}

	public void uploadAvro(SchemaView schemaView, InputStream is, FormDataContentDisposition header) throws IOException,
	      DalException, RestClientException {
		byte[] fileBytes = ByteStreams.toByteArray(is);
		Schema metaSchema = schemaView.toMetaSchema();
		metaSchema.setFileContent(fileBytes);
		metaSchema.setFileProperties(header.toString());

		Parser parser = new Parser();
		org.apache.avro.Schema avroSchema = parser.parse(new String(fileBytes));
		int avroid = avroSchemaRegistry.register(metaSchema.getName(), avroSchema);
		metaSchema.setAvroid(avroid);
		schemaDao.updateByPK(metaSchema, SchemaEntity.UPDATESET_FULL);
	}

	public void uploadJson(SchemaView schemaView, InputStream is, FormDataContentDisposition header) throws IOException,
	      DalException {
		byte[] fileBytes = ByteStreams.toByteArray(is);
		Schema metaSchema = schemaView.toMetaSchema();
		metaSchema.setFileContent(fileBytes);
		metaSchema.setFileProperties(header.toString());
		schemaDao.updateByPK(metaSchema, SchemaEntity.UPDATESET_FULL);
	}

}
