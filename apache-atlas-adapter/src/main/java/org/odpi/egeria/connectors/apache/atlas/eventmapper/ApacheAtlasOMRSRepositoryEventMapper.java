/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.eventmapper;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.AtlasRelationship;
import org.apache.atlas.model.instance.AtlasRelationshipHeader;
import org.apache.atlas.model.notification.EntityNotification;
import org.apache.atlas.notification.entity.EntityMessageDeserializer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSAuditCode;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSMetadataCollection;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSRepositoryConnector;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping.EntityMappingAtlas2OMRS;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping.RelationshipMapping;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model.AtlasGuid;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.repositoryservices.connectors.openmetadatatopic.OpenMetadataTopicListener;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.AttributeTypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefPatch;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryeventmapper.OMRSRepositoryEventMapperBase;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ApacheAtlasOMRSRepositoryEventMapper supports the event mapper function for Apache Atlas
 * when used as an open metadata repository.
 */
public class ApacheAtlasOMRSRepositoryEventMapper extends OMRSRepositoryEventMapperBase
        implements OpenMetadataTopicListener {

    private static final Logger log = LoggerFactory.getLogger(ApacheAtlasOMRSRepositoryEventMapper.class);
    private static final Duration pollDuration = Duration.ofMillis(100);

    private String sourceName;
    private ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector;
    private ApacheAtlasOMRSMetadataCollection atlasMetadataCollection;
    private TypeDefStore typeDefStore;
    private String metadataCollectionId;
    private String originatorServerName;
    private String originatorServerType;

    private Properties atlasKafkaProperties;
    private String atlasKafkaTopic;

    private KafkaConsumerThread kafkaConsumer;
    private EntityMessageDeserializer deserializer;

    /**
     * Default constructor
     */
    public ApacheAtlasOMRSRepositoryEventMapper() {
        super();
        this.sourceName = "ApacheAtlasOMRSRepositoryEventMapper";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws ConnectorCheckedException {

        super.start();

        final String methodName = "start";

        auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.EVENT_MAPPER_STARTING.getMessageDefinition());

        if ( !(repositoryConnector instanceof ApacheAtlasOMRSRepositoryConnector) ) {
            raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode.EVENT_MAPPER_IMPROPERLY_INITIALIZED, methodName, null, repositoryConnector.getServerName());
        }
        this.atlasRepositoryConnector = (ApacheAtlasOMRSRepositoryConnector) this.repositoryConnector;
        this.atlasKafkaTopic = "ATLAS_ENTITIES";

        // Retrieve connection details to configure Kafka connectivity
        String atlasKafkaBootstrap = this.connectionBean.getEndpoint().getAddress();
        atlasKafkaProperties = new Properties();
        atlasKafkaProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, atlasKafkaBootstrap);
        atlasKafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "ApacheAtlasOMRSRepositoryEventMapper_consumer");
        atlasKafkaProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        atlasKafkaProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        this.deserializer = new EntityMessageDeserializer();

        this.kafkaConsumer = new KafkaConsumerThread();
        try {
            this.atlasMetadataCollection = (ApacheAtlasOMRSMetadataCollection) atlasRepositoryConnector.getMetadataCollection();
        } catch (RepositoryErrorException e) {
            raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode.REST_CLIENT_FAILURE, methodName, e, atlasRepositoryConnector.getServerName());
        }
        this.typeDefStore = atlasMetadataCollection.getTypeDefStore();
        atlasMetadataCollection.setEventMapper(this);
        this.metadataCollectionId = atlasRepositoryConnector.getMetadataCollectionId();
        this.originatorServerName = atlasRepositoryConnector.getServerName();
        this.originatorServerType = atlasRepositoryConnector.getServerType();
        kafkaConsumer.start();

    }


    /**
     * Class to support multi-threaded consumption of Apache Atlas Kafka events.
     */
    private class KafkaConsumerThread implements Runnable {

        private final AtomicBoolean running = new AtomicBoolean(false);

        void start() {
            Thread worker = new Thread(this);
            worker.start();
        }

        void stop() {
            running.set(false);
        }

        /**
         * Read Apache Atlas Kafka events.
         */
        @Override
        public void run() {

            final String methodName = "run";

            running.set(true);
            try (final Consumer<Long, String> consumer = new KafkaConsumer<>(atlasKafkaProperties)) {
                consumer.subscribe(Collections.singletonList(atlasKafkaTopic));
                auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.EVENT_MAPPER_RUNNING.getMessageDefinition(atlasRepositoryConnector.getServerName()));
                while (running.get()) {
                    try {
                        ConsumerRecords<Long, String> events = consumer.poll(pollDuration);
                        for (ConsumerRecord<Long, String> event : events) {
                            processEvent(event.value());
                        }
                    } catch (Exception e) {
                        auditLog.logException(methodName, ApacheAtlasOMRSAuditCode.EVENT_MAPPER_CONSUMER_FAILURE.getMessageDefinition(), e);
                    }
                }
            }
        }

    }


    /**
     * Method to pass an event received on topic.
     *
     * @param event inbound event
     */
    @Override
    public void processEvent(String event) {
        log.info("Processing event: {}", event);

        // Need to call this with just the 'message' portion of the payload, it seems?
        EntityNotification atlasEvent = deserializer.deserialize(event);
        EntityNotification.EntityNotificationV2 entityNotification = (EntityNotification.EntityNotificationV2) atlasEvent;

        if (entityNotification != null) {

            // TODO: create examples for and test commented-out operations

            switch(entityNotification.getOperationType()) {
                case ENTITY_CREATE:
                    processNewEntity(entityNotification.getEntity());
                    break;
                case ENTITY_UPDATE:
                    processUpdatedEntity(entityNotification.getEntity());
                    break;
                case ENTITY_DELETE:
                    processDeletedEntity(entityNotification.getEntity());
                    break;
                /*case CLASSIFICATION_ADD:
                    break;
                case CLASSIFICATION_UPDATE:
                    break;
                case CLASSIFICATION_DELETE:
                    break;*/
                case RELATIONSHIP_CREATE:
                    processNewRelationship(entityNotification.getRelationship());
                    break;
                /*case RELATIONSHIP_UPDATE:
                    break;*/
                case RELATIONSHIP_DELETE:
                    processDeletedRelationship(entityNotification.getRelationship());
                    break;
                default:
                    log.warn("Unrecognized operation type from Apache Atlas: {}", event);
                    break;
            }

        } else {
            log.error("Unrecognized event type from Apache Atlas: {}", event);
        }

    }

    /**
     * Processes and sends an OMRS event for the new Apache Atlas entity.
     *
     * @param atlasEntityHeader the new Apache Atlas entity information
     */
    private void processNewEntity(AtlasEntityHeader atlasEntityHeader) {
        // Send an event for every entity: normal and generated
        String atlasTypeName = atlasEntityHeader.getTypeName();
        Map<String, String> omrsTypesByPrefix = typeDefStore.getAllMappedOMRSTypeDefNames(atlasTypeName);
        if (omrsTypesByPrefix != null) {
            // Note: these mappings will be for both entities and self-referencing relationships.
            for (Map.Entry<String, String> entry : omrsTypesByPrefix.entrySet()) {
                String prefix = entry.getKey();
                String omrsTypeName = entry.getValue();
                // We need to ensure this OMRS type is actually for an entity before attempting to map it as one
                TypeDef typeDef = typeDefStore.getTypeDefByName(omrsTypeName);
                if (typeDef.getCategory() == TypeDefCategory.ENTITY_DEF) {
                    EntityDetail entityDetail = getMappedEntity(atlasEntityHeader, prefix);
                    if (entityDetail != null) {
                        repositoryEventProcessor.processNewEntityEvent(
                                sourceName,
                                metadataCollectionId,
                                originatorServerName,
                                originatorServerType,
                                localOrganizationName,
                                entityDetail
                        );
                        // If the entity was mapped, also send events for any self-referencing (generated) relationships
                        List<Relationship> generatedRelationships = getGeneratedRelationshipsForEntity(atlasEntityHeader, prefix);
                        for (Relationship generatedRelationship : generatedRelationships) {
                            repositoryEventProcessor.processNewRelationshipEvent(
                                    sourceName,
                                    metadataCollectionId,
                                    originatorServerName,
                                    originatorServerType,
                                    localOrganizationName,
                                    generatedRelationship
                            );
                        }
                    }
                }
            }
        } else {
            log.warn("Unmapped Atlas type, cannot process event: {}", atlasTypeName);
        }
    }

    /**
     * Processes and sends an OMRS event for the updated Apache Atlas entity.
     *
     * @param atlasEntityHeader the updated Apache Atlas entity information
     */
    private void processUpdatedEntity(AtlasEntityHeader atlasEntityHeader) {
        // Send an event for every entity: normal and generated
        Map<String, String> omrsTypesByPrefix = typeDefStore.getAllMappedOMRSTypeDefNames(atlasEntityHeader.getTypeName());
        if (omrsTypesByPrefix != null) {
            // Note: these mappings will be for both entities and self-referencing relationships.
            for (Map.Entry<String, String> entry : omrsTypesByPrefix.entrySet()) {
                String prefix = entry.getKey();
                String omrsTypeName = entry.getValue();
                // We need to ensure this OMRS type is actually for an entity before attempting to map it as one
                TypeDef typeDef = typeDefStore.getTypeDefByName(omrsTypeName);
                if (typeDef.getCategory() == TypeDefCategory.ENTITY_DEF) {
                    EntityDetail entityDetail = getMappedEntity(atlasEntityHeader, prefix);
                    if (entityDetail != null) {
                        // TODO: find a way to pull back the old version to send in the update event
                        //  (for now we will just fake one based on the latest version of the entity)
                        EntityDetail oldE = new EntityDetail(entityDetail);
                        oldE.setProperties(null);
                        oldE.setVersion(-1L);
                        repositoryEventProcessor.processUpdatedEntityEvent(
                                sourceName,
                                metadataCollectionId,
                                originatorServerName,
                                originatorServerType,
                                localOrganizationName,
                                oldE,
                                entityDetail
                        );
                        // If the entity was mapped, also send events for any self-referencing (generated) relationships
                        List<Relationship> generatedRelationships = getGeneratedRelationshipsForEntity(atlasEntityHeader, prefix);
                        for (Relationship generatedRelationship : generatedRelationships) {
                            // TODO: find a way to pull back the old version to send in the update event
                            //  (for now we will just fake one based on the latest version of the relationship)
                            Relationship oldR = new Relationship(generatedRelationship);
                            oldR.setProperties(null);
                            oldR.setVersion(-1L);
                            repositoryEventProcessor.processUpdatedRelationshipEvent(
                                    sourceName,
                                    metadataCollectionId,
                                    originatorServerName,
                                    originatorServerType,
                                    localOrganizationName,
                                    oldR,
                                    generatedRelationship
                            );
                        }
                    }
                }
            }
        } else {
            log.warn("Unable to process an entity update for unmapped type: {}", atlasEntityHeader.getTypeName());
        }
    }

    /**
     * Processes and sends an OMRS event for the deleted Apache Atlas entity.
     *
     * @param atlasEntityHeader the deleted Apache Atlas entity information
     */
    private void processDeletedEntity(AtlasEntityHeader atlasEntityHeader) {
        Map<String, String> omrsTypesByPrefix = typeDefStore.getAllMappedOMRSTypeDefNames(atlasEntityHeader.getTypeName());
        if (omrsTypesByPrefix != null) {
            // Note: these mappings will be for both entities and self-referencing relationships.
            for (Map.Entry<String, String> entry : omrsTypesByPrefix.entrySet()) {
                String prefix = entry.getKey();
                String omrsTypeName = entry.getValue();
                // We need to ensure this OMRS type is actually for an entity before attempting to map it as one
                TypeDef typeDef = typeDefStore.getTypeDefByName(omrsTypeName);
                if (typeDef.getCategory() == TypeDefCategory.ENTITY_DEF) {
                    AtlasGuid entityGUID = new AtlasGuid(atlasEntityHeader.getGuid(), prefix);
                    try {
                        EntityDetail entityDetail = repositoryHelper.getSkeletonEntity(
                                sourceName,
                                metadataCollectionId,
                                InstanceProvenanceType.LOCAL_COHORT,
                                null,
                                typeDef.getName()
                        );
                        entityDetail.setGUID(entityGUID.toString());
                        repositoryEventProcessor.processDeletedEntityEvent(
                                sourceName,
                                metadataCollectionId,
                                originatorServerName,
                                originatorServerType,
                                localOrganizationName,
                                entityDetail
                        );
                        // Also send events for any self-referencing (generated) relationships
                        List<Relationship> generatedRelationships = getGeneratedRelationshipsForEntity(atlasEntityHeader, prefix);
                        for (Relationship generatedRelationship : generatedRelationships) {
                            repositoryEventProcessor.processDeletedRelationshipEvent(
                                    sourceName,
                                    metadataCollectionId,
                                    originatorServerName,
                                    originatorServerType,
                                    localOrganizationName,
                                    generatedRelationship
                            );
                        }
                    } catch (TypeErrorException e) {
                        log.error("Unable to process an entity delete for: {}", entityGUID, e);
                    }
                }
            }
        } else {
            log.warn("Unable to process an entity delete for unmapped type: {}", atlasEntityHeader.getTypeName());
        }
    }

    /**
     * Processes and sends an OMRS event for the deleted Apache Atlas relationship.
     *
     * @param atlasRelationshipHeader the deleted Apache Atlas relationship information
     */
    private void processDeletedRelationship(AtlasRelationshipHeader atlasRelationshipHeader) {
        Map<String, String> omrsTypesByPrefix = typeDefStore.getAllMappedOMRSTypeDefNames(atlasRelationshipHeader.getTypeName());
        if (omrsTypesByPrefix != null) {
            for (Map.Entry<String, String> entry : omrsTypesByPrefix.entrySet()) {
                String prefix = entry.getKey();
                String omrsTypeName = entry.getValue();
                // We need to ensure this OMRS type is actually for a relationship before attempting to map it as one
                TypeDef typeDef = typeDefStore.getTypeDefByName(omrsTypeName);
                if (typeDef.getCategory() == TypeDefCategory.RELATIONSHIP_DEF) {
                    AtlasGuid relationshipGUID = new AtlasGuid(atlasRelationshipHeader.getGuid(), prefix);
                    try {
                        Relationship relationship = repositoryHelper.getSkeletonRelationship(
                                sourceName,
                                metadataCollectionId,
                                InstanceProvenanceType.LOCAL_COHORT,
                                null,
                                typeDef.getName()
                        );
                        relationship.setGUID(relationshipGUID.toString());
                        repositoryEventProcessor.processDeletedRelationshipEvent(
                                sourceName,
                                metadataCollectionId,
                                originatorServerName,
                                originatorServerType,
                                localOrganizationName,
                                relationship
                        );
                    } catch (TypeErrorException e) {
                        log.error("Unable to process a relationship delete for: {}", relationshipGUID, e);
                    }
                }
            }
        } else {
            log.warn("Unable to process a relationship delete for unmapped type: {}", atlasRelationshipHeader.getTypeName());
        }
    }

    /**
     * Generate any pseudo-relationships for the provided entity.
     *
     * @param atlasEntityHeader the Atlas entity for which to generate pseudo-relationships
     * @param prefix if the Atlas entity is a generated one
     * @return {@code List<Relationship>}
     */
    private List<Relationship> getGeneratedRelationshipsForEntity(AtlasEntityHeader atlasEntityHeader,
                                                                  String prefix) {

        String atlasTypeName = atlasEntityHeader.getTypeName();
        List<Relationship> generatedRelationships = new ArrayList<>();
        Map<String, TypeDefStore.EndpointMapping> mappings = typeDefStore.getAllEndpointMappingsFromAtlasName(atlasTypeName);
        if (mappings != null) {
            for (Map.Entry<String, TypeDefStore.EndpointMapping> entry : mappings.entrySet()) {
                String relationshipPrefix = entry.getKey();
                if (relationshipPrefix != null) {
                    AtlasGuid atlasGuid = new AtlasGuid(atlasEntityHeader.getGuid(), relationshipPrefix);
                    try {
                        Relationship generatedRelationship = RelationshipMapping.getSelfReferencingRelationship(
                                atlasRepositoryConnector,
                                typeDefStore,
                                atlasGuid,
                                new AtlasEntity(atlasEntityHeader)
                        );
                        if (generatedRelationship != null) {
                            generatedRelationships.add(generatedRelationship);
                        } else {
                            log.warn("Unable to create generated relationship with prefix {}, for entity: {}", relationshipPrefix, new AtlasGuid(atlasEntityHeader.getGuid(), prefix));
                        }
                    } catch (RepositoryErrorException e) {
                        log.error("Unable to create generated relationship with prefix {}, for entity: {}", relationshipPrefix, new AtlasGuid(atlasEntityHeader.getGuid(), prefix), e);
                    }
                }
            }
        }
        return generatedRelationships;

    }

    /**
     * Retrieve the mapped OMRS entity for the provided Apache Atlas entity.
     *
     * @param atlasEntityHeader the Apache Atlas entity to translate to OMRS
     * @return EntityDetail
     */
    private EntityDetail getMappedEntity(AtlasEntityHeader atlasEntityHeader, String prefix) {
        EntityDetail result = null;
        AtlasEntity.AtlasEntityWithExtInfo atlasEntity = null;
        try {
            atlasEntity = atlasRepositoryConnector.getEntityByGUID(atlasEntityHeader.getGuid(), false, true);
        } catch (AtlasServiceException e) {
            log.error("Unable to retrieve entity from Atlas: {}", atlasEntityHeader, e);
        }
        EntityMappingAtlas2OMRS mapping = new EntityMappingAtlas2OMRS(
                atlasRepositoryConnector,
                atlasMetadataCollection.getTypeDefStore(),
                atlasMetadataCollection.getAttributeTypeDefStore(),
                atlasEntity,
                prefix,
                null
        );
        try {
            result = mapping.getEntityDetail();
        } catch (RepositoryErrorException e) {
            log.error("Unable to map entity to OMRS EntityDetail: {}", atlasEntity, e);
        }
        return result;
    }

    /**
     * Processes and sends an OMRS event for the new Apache Atlas relationship.
     *
     * @param atlasRelationshipHeader the new Apache Atlas relationship information
     */
    private void processNewRelationship(AtlasRelationshipHeader atlasRelationshipHeader) {
        Relationship relationship = getMappedRelationship(atlasRelationshipHeader);
        if (relationship != null) {
            repositoryEventProcessor.processNewRelationshipEvent(
                    sourceName,
                    metadataCollectionId,
                    originatorServerName,
                    originatorServerType,
                    localOrganizationName,
                    relationship
            );
        }
    }

    /**
     * Retrieve the mapped OMRS relationship for the provided Apache Atlas relationship.
     *
     * @param atlasRelationshipHeader the Apache Atlas relationship to translate to OMRS
     * @return Relationship
     */
    private Relationship getMappedRelationship(AtlasRelationshipHeader atlasRelationshipHeader) {
        // TODO: this needs to handle both self-referencing (generated) relationships and "normal" relationships,
        //  currently only handling the latter?
        Relationship result = null;
        AtlasRelationship.AtlasRelationshipWithExtInfo atlasRelationship = null;
        try {
            atlasRelationship = atlasRepositoryConnector.getRelationshipByGUID(atlasRelationshipHeader.getGuid(), true);
        } catch (AtlasServiceException e) {
            log.error("Unable to retrieve relationship from Atlas: {}", atlasRelationshipHeader, e);
        }
        if (atlasRelationship != null) {
            RelationshipMapping mapping = new RelationshipMapping(
                    atlasRepositoryConnector,
                    atlasMetadataCollection.getTypeDefStore(),
                    atlasMetadataCollection.getAttributeTypeDefStore(),
                    new AtlasGuid(atlasRelationshipHeader.getGuid(), null),
                    atlasRelationship,
                    null
            );
            try {
                result = mapping.getRelationship();
            } catch (RepositoryErrorException e) {
                log.error("Unable to map relationship to OMRS Relationship: {}", atlasRelationship, e);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect() throws ConnectorCheckedException {
        super.disconnect();
        final String methodName = "disconnect";
        kafkaConsumer.stop();
        auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.EVENT_MAPPER_SHUTDOWN.getMessageDefinition(atlasRepositoryConnector.getServerName()));
    }

    /**
      * Sends a new TypeDef event.
      *
      * @param newTypeDef the new TypeDef for which to send an event
      */
    public void sendNewTypeDefEvent(TypeDef newTypeDef) {
        repositoryEventProcessor.processNewTypeDefEvent(
                sourceName,
                metadataCollectionId,
                localServerName,
                localServerType,
                localOrganizationName,
                newTypeDef
        );
    }

    /**
     * Sends a new AttributeTypeDef event.
     *
     * @param newAttributeTypeDef the new AttributeTypeDef for which to send an event
     */
    public void sendNewAttributeTypeDefEvent(AttributeTypeDef newAttributeTypeDef) {
        repositoryEventProcessor.processNewAttributeTypeDefEvent(
                sourceName,
                metadataCollectionId,
                localServerName,
                localServerType,
                localOrganizationName,
                newAttributeTypeDef);
    }

    /**
     * Sends an updated TypeDef event.
     *
     * @param typeDefPatch the patch that was applied to achieve the update
     */
    public void sendUpdatedTypeDefEvent(TypeDefPatch typeDefPatch) {
        repositoryEventProcessor.processUpdatedTypeDefEvent(
                sourceName,
                metadataCollectionId,
                localServerName,
                localServerType,
                localOrganizationName,
                typeDefPatch);
    }

    /**
     * Sends a refresh entity request event.
     *
     * @param typeDefGUID unique identifier of requested entity's TypeDef
     * @param typeDefName unique name of requested entity's TypeDef
     * @param entityGUID unique identifier of requested entity
     * @param homeMetadataCollectionId identifier of the metadata collection that is the home to this entity
     */
    public void sendRefreshEntityRequest(String typeDefGUID,
                                         String typeDefName,
                                         String entityGUID,
                                         String homeMetadataCollectionId) {
        repositoryEventProcessor.processRefreshEntityRequested(
                sourceName,
                metadataCollectionId,
                localServerName,
                localServerType,
                localOrganizationName,
                typeDefGUID,
                typeDefName,
                entityGUID,
                homeMetadataCollectionId);
    }

    /**
     * Sends a refresh relationship request event.
     *
     * @param typeDefGUID the guid of the TypeDef for the relationship used to verify the relationship identity
     * @param typeDefName the name of the TypeDef for the relationship used to verify the relationship identity
     * @param relationshipGUID unique identifier of the relationship
     * @param homeMetadataCollectionId unique identifier for the home repository for this relationship
     */
    public void sendRefreshRelationshipRequest(String typeDefGUID,
                                               String typeDefName,
                                               String relationshipGUID,
                                               String homeMetadataCollectionId) {
        repositoryEventProcessor.processRefreshRelationshipRequest(
                sourceName,
                metadataCollectionId,
                localServerName,
                localServerType,
                localOrganizationName,
                typeDefGUID,
                typeDefName,
                relationshipGUID,
                homeMetadataCollectionId);
    }

    /**
     * Throws a ConnectorCheckedException based on the provided parameters.
     *
     * @param errorCode the error code for the exception
     * @param methodName the method name throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any additional parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Exception cause, String ...params) throws ConnectorCheckedException {
        if (cause == null) {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

}
