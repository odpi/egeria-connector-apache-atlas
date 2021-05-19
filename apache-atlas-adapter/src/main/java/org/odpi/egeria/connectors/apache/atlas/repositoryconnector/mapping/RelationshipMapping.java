/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.model.instance.AtlasRelationship;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSRepositoryConnector;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model.AtlasGuid;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.AttributeTypeDefStore;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.RelationshipDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefAttribute;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;

/**
 * The base class for all mappings between OMRS Relationship instances and Apache Atlas relationship instances.
 */
public class RelationshipMapping {

    private static final Logger log = LoggerFactory.getLogger(RelationshipMapping.class);

    private ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector;
    private TypeDefStore typeDefStore;
    private AttributeTypeDefStore attributeDefStore;
    private AtlasGuid atlasGuid;
    private AtlasRelationship atlasRelationship;
    private String userId;

    /**
     * Mapping itself must be initialized with various objects.
     *
     * @param atlasRepositoryConnector connectivity to an Apache Atlas repository
     * @param typeDefStore the store of mapped TypeDefs for the Atlas repository
     * @param attributeDefStore the store of mapped AttributeTypeDefs for the Atlas repository
     * @param atlasGuid the GUID that was used to retrieve this relationship
     * @param instance the Atlas relationship to be mapped
     * @param userId the user through which to do the mapping
     */
    public RelationshipMapping(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                               TypeDefStore typeDefStore,
                               AttributeTypeDefStore attributeDefStore,
                               AtlasGuid atlasGuid,
                               AtlasRelationship.AtlasRelationshipWithExtInfo instance,
                               String userId) {
        this.atlasRepositoryConnector = atlasRepositoryConnector;
        this.typeDefStore = typeDefStore;
        this.attributeDefStore = attributeDefStore;
        this.atlasGuid = atlasGuid;
        this.atlasRelationship = instance.getRelationship();
        this.userId = userId;
    }

    /**
     * Retrieve the mapped OMRS Relationship from the Apache Atlas AtlasRelationship used to construct this mapping object.
     *
     * @return Relationship
     * @throws RepositoryErrorException when unable to retrieve the Relationship
     */
    public Relationship getRelationship() throws RepositoryErrorException {

        final String methodName = "getRelationship";
        String repositoryName = atlasRepositoryConnector.getRepositoryName();

        String atlasRelationshipType = atlasRelationship.getTypeName();
        String relationshipPrefix = atlasGuid.getGeneratedPrefix();

        TypeDefStore.EndpointMapping mapping = typeDefStore.getEndpointMappingFromAtlasName(atlasRelationshipType, relationshipPrefix);

        Relationship omrsRelationship = null;
        if (mapping != null) {
            // TODO: assumes the endpoints are always the same ends
            AtlasObjectId atlasEp1 = atlasRelationship.getEnd1();
            AtlasObjectId atlasEp2 = atlasRelationship.getEnd2();

            EntityProxy ep1 = null;
            EntityProxy ep2 = null;

            try {
                ep1 = RelationshipMapping.getEntityProxyForObject(
                        atlasRepositoryConnector,
                        typeDefStore,
                        atlasRepositoryConnector.getEntityByGUID(atlasEp1.getGuid(), true, true).getEntity(),
                        mapping.getPrefixOne(),
                        userId
                );
            } catch (AtlasServiceException e) {
                raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, atlasEp1.getGuid(), methodName, repositoryName);
            }
            try {
                ep2 = RelationshipMapping.getEntityProxyForObject(
                        atlasRepositoryConnector,
                        typeDefStore,
                        atlasRepositoryConnector.getEntityByGUID(atlasEp2.getGuid(), true, true).getEntity(),
                        mapping.getPrefixTwo(),
                        userId
                );
            } catch (AtlasServiceException e) {
                raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, atlasEp2.getGuid(), methodName, repositoryName);
            }

            if (ep1 != null && ep2 != null) {
                omrsRelationship = getRelationship(
                        atlasRelationshipType,
                        ep1,
                        ep2);
            }
        } else {
            log.warn("Unmapped relationship type, skipping: {}{}", relationshipPrefix == null ? "" : relationshipPrefix + "#", atlasRelationshipType);
        }

        return omrsRelationship;

    }

    /**
     * Create a mapped relationship based on the provided criteria
     *
     * @param atlasRelationshipType the type of the Atlas relationship to map
     * @param ep1 the proxy to map to endpoint 1
     * @param ep2 the proxy to map to endpoint 2
     * @return Relationship
     * @throws RepositoryErrorException when unable to map the Relationship
     */
    private Relationship getRelationship(String atlasRelationshipType,
                                         EntityProxy ep1,
                                         EntityProxy ep2) throws RepositoryErrorException {

        final String methodName = "getRelationship";
        OMRSRepositoryHelper omrsRepositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
        String repositoryName = atlasRepositoryConnector.getRepositoryName();

        String omrsRelationshipType = typeDefStore.getMappedOMRSTypeDefName(atlasRelationshipType, atlasGuid.getGeneratedPrefix());

        InstanceStatus omrsRelationshipStatus;
        AtlasRelationship.Status relationshipStatus = atlasRelationship.getStatus();
        switch (relationshipStatus) {
            case ACTIVE:
                omrsRelationshipStatus = InstanceStatus.ACTIVE;
                break;
            case DELETED:
                omrsRelationshipStatus = InstanceStatus.DELETED;
                break;
            default:
                log.warn("Unhandled relationship status, defaulting to ACTIVE: {}", relationshipStatus);
                omrsRelationshipStatus = InstanceStatus.ACTIVE;
                break;
        }

        Map<String, Object> atlasRelationshipProperties = atlasRelationship.getAttributes();
        InstanceProperties omrsRelationshipProperties = new InstanceProperties();
        if (atlasRelationshipProperties != null) {

            Map<String, TypeDefAttribute> relationshipAttributeMap = typeDefStore.getAllTypeDefAttributesForName(omrsRelationshipType);
            Map<String, String> atlasToOmrsProperties = typeDefStore.getPropertyMappingsForAtlasTypeDef(atlasRelationshipType, atlasGuid.getGeneratedPrefix());
            if (atlasToOmrsProperties != null) {

                for (Map.Entry<String, String> property : atlasToOmrsProperties.entrySet()) {
                    String atlasProperty = property.getKey();
                    String omrsProperty = property.getValue();
                    if (relationshipAttributeMap.containsKey(omrsProperty)) {
                        TypeDefAttribute typeDefAttribute = relationshipAttributeMap.get(omrsProperty);
                        omrsRelationshipProperties = AttributeMapping.addPropertyToInstance(omrsRepositoryHelper,
                                repositoryName,
                                typeDefAttribute,
                                omrsRelationshipProperties,
                                attributeDefStore,
                                atlasRelationshipProperties.get(atlasProperty),
                                methodName);
                    } else {
                        log.warn("No OMRS attribute {} defined for asset type {} -- skipping mapping.", omrsProperty, omrsRelationshipType);
                    }
                }

            }

        }

        return RelationshipMapping.getRelationship(
                atlasRepositoryConnector,
                typeDefStore,
                omrsRelationshipType,
                atlasGuid,
                omrsRelationshipStatus,
                ep1,
                ep2,
                atlasRelationship.getCreatedBy(),
                atlasRelationship.getUpdatedBy(),
                atlasRelationship.getCreateTime(),
                atlasRelationship.getUpdateTime(),
                omrsRelationshipProperties);

    }

    /**
     * Setup a self-referencing relationship using the provided prefix and Apache Atlas entity.
     *
     * @param atlasRepositoryConnector connectivity to an Apache Atlas environment
     * @param typeDefStore store of TypeDef mappings
     * @param relationshipGUID the GUID of the relationship
     * @param entity the entity for which the self-referencing relationship should be generated
     * @return Relationship
     * @throws RepositoryErrorException when unable to map the Relationship
     */
    public static Relationship getSelfReferencingRelationship(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                                                              TypeDefStore typeDefStore,
                                                              AtlasGuid relationshipGUID,
                                                              AtlasEntity entity) throws RepositoryErrorException {

        Relationship omrsRelationship = null;
        String prefix = relationshipGUID.getGeneratedPrefix();
        if (prefix != null) {

            RelationshipDef relationshipDef = (RelationshipDef) typeDefStore.getTypeDefByPrefix(prefix);
            String omrsRelationshipType = relationshipDef.getName();
            TypeDefStore.EndpointMapping mapping = typeDefStore.getEndpointMappingFromAtlasName(entity.getTypeName(), prefix);
            EntityProxy ep1 = RelationshipMapping.getEntityProxyForObject(
                    atlasRepositoryConnector,
                    typeDefStore,
                    entity,
                    mapping.getPrefixOne(),
                    null
            );
            EntityProxy ep2 = RelationshipMapping.getEntityProxyForObject(
                    atlasRepositoryConnector,
                    typeDefStore,
                    entity,
                    mapping.getPrefixTwo(),
                    null
            );
            // TODO: assumes that properties on a self-generated relationship are always empty
            omrsRelationship = getRelationship(atlasRepositoryConnector,
                    typeDefStore,
                    omrsRelationshipType,
                    relationshipGUID,
                    InstanceStatus.ACTIVE,
                    ep1,
                    ep2,
                    entity.getCreatedBy(),
                    entity.getUpdatedBy(),
                    entity.getCreateTime(),
                    entity.getUpdateTime(),
                    new InstanceProperties());
        } else {
            log.error("A self-referencing, generated relationship was requested, but there is no prefix: {}", relationshipGUID);
        }

        return omrsRelationship;

    }

    /**
     * Create a mapped relationship based on the provided criteria
     *
     * @param atlasRepositoryConnector connectivity to an Apache Atlas environment
     * @param typeDefStore store of TypeDef mappings
     * @param omrsRelationshipType the type of the OMRS relationship to map
     * @param relationshipGUID the GUID of the relationship
     * @param relationshipStatus the status of the relationship
     * @param ep1 the proxy to map to endpoint 1
     * @param ep2 the proxy to map to endpoint 2
     * @param createdBy the relationship creator
     * @param updatedBy the relationship updator
     * @param createTime the time the relationship was created
     * @param updateTime the time the relationship was updated
     * @param omrsRelationshipProperties the properties to set on the relationship
     * @return Relationship
     * @throws RepositoryErrorException when unable to map the Relationship
     */
    private static Relationship getRelationship(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                                                TypeDefStore typeDefStore,
                                                String omrsRelationshipType,
                                                AtlasGuid relationshipGUID,
                                                InstanceStatus relationshipStatus,
                                                EntityProxy ep1,
                                                EntityProxy ep2,
                                                String createdBy,
                                                String updatedBy,
                                                Date createTime,
                                                Date updateTime,
                                                InstanceProperties omrsRelationshipProperties) throws RepositoryErrorException {

        final String methodName = "getRelationship";
        String repositoryName = atlasRepositoryConnector.getRepositoryName();

        Relationship omrsRelationship = RelationshipMapping.getSkeletonRelationship(
                atlasRepositoryConnector,
                (RelationshipDef) typeDefStore.getTypeDefByName(omrsRelationshipType)
        );

        long version = 1L;
        if (updateTime != null) {
            version = updateTime.getTime();
        }

        omrsRelationship.setGUID(relationshipGUID.toString());
        omrsRelationship.setMetadataCollectionId(atlasRepositoryConnector.getMetadataCollectionId());
        omrsRelationship.setStatus(relationshipStatus);
        omrsRelationship.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        omrsRelationship.setVersion(version);
        omrsRelationship.setCreateTime(createTime);
        omrsRelationship.setCreatedBy(createdBy);
        omrsRelationship.setUpdatedBy(updatedBy);
        omrsRelationship.setUpdateTime(updateTime);

        if (ep1 != null && ep2 != null) {
            omrsRelationship.setEntityOneProxy(ep1);
            omrsRelationship.setEntityTwoProxy(ep2);
        } else {
            throw new RepositoryErrorException(ApacheAtlasOMRSErrorCode.INVALID_RELATIONSHIP_ENDS.getMessageDefinition(methodName, repositoryName, omrsRelationshipType, ep1 == null ? "null" : ep1.getGUID(), ep2 == null ? "null" : ep2.getGUID()),
                    RelationshipMapping.class.getName(),
                    methodName);
        }

        if (omrsRelationshipProperties != null) {
            omrsRelationship.setProperties(omrsRelationshipProperties);
        }

        return omrsRelationship;

    }

    /**
     * Retrieves an EntityProxy object for the provided Apache Atlas object.
     *
     * @param atlasRepositoryConnector OMRS connector to the Apache Atlas repository
     * @param typeDefStore store of mapped TypeDefs
     * @param atlasObj the Apache Atlas object for which to retrieve an EntityProxy
     * @param entityPrefix the prefix used for the entity, if it is a generated entity (null if not generated)
     * @param userId the user through which to retrieve the EntityProxy (unused)
     * @return EntityProxy
     */
    public static EntityProxy getEntityProxyForObject(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                                                      TypeDefStore typeDefStore,
                                                      AtlasEntity atlasObj,
                                                      String entityPrefix,
                                                      String userId) {

        final String methodName = "getEntityProxyForObject";

        EntityProxy entityProxy = null;
        if (atlasObj != null) {

            String repositoryName = atlasRepositoryConnector.getRepositoryName();
            OMRSRepositoryHelper repositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
            String metadataCollectionId = atlasRepositoryConnector.getMetadataCollectionId();

            String atlasTypeName = atlasObj.getTypeName();
            String omrsTypeDefName = typeDefStore.getMappedOMRSTypeDefName(atlasTypeName, entityPrefix);

            String qualifiedName;
            Map<String, Object> attributes = atlasObj.getAttributes();
            if (attributes.containsKey("qualifiedName")) {
                qualifiedName = (String) attributes.get("qualifiedName");
            } else {
                log.error("No qualifiedName found for object -- cannot create EntityProxy: {}", atlasObj);
                throw new NullPointerException("No qualifiedName found for object -- cannot create EntityProxy.");
            }

            InstanceProperties uniqueProperties = repositoryHelper.addStringPropertyToInstance(
                    repositoryName,
                    null,
                    "qualifiedName",
                    qualifiedName,
                    methodName
            );

            try {
                entityProxy = repositoryHelper.getNewEntityProxy(
                        repositoryName,
                        metadataCollectionId,
                        InstanceProvenanceType.LOCAL_COHORT,
                        userId,
                        omrsTypeDefName,
                        uniqueProperties,
                        null
                );
                AtlasGuid atlasGuid = new AtlasGuid(atlasObj.getGuid(), entityPrefix);
                entityProxy.setGUID(atlasGuid.toString());
                entityProxy.setCreatedBy(atlasObj.getCreatedBy());
                entityProxy.setCreateTime(atlasObj.getCreateTime());
                entityProxy.setUpdatedBy(atlasObj.getUpdatedBy());
                entityProxy.setUpdateTime(atlasObj.getUpdateTime());
                entityProxy.setVersion(atlasObj.getVersion());
            } catch (TypeErrorException e) {
                log.error("Unable to create new EntityProxy.", e);
            }

        } else {
            log.error("No Apache Atlas object provided (was null).");
        }

        return entityProxy;

    }

    /**
     * Create the base skeleton of a Relationship, irrespective of the specific Apache Atlas object.
     *
     * @param atlasRepositoryConnector connectivity to an Apache Atlas environment
     * @param omrsRelationshipDef the OMRS RelationshipDef for which to create a skeleton Relationship
     * @return Relationship
     * @throws RepositoryErrorException when unable to create a new skeletal Relationship
     */
    static Relationship getSkeletonRelationship(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                                                RelationshipDef omrsRelationshipDef) throws RepositoryErrorException {

        final String methodName = "getSkeletonRelationship";
        Relationship relationship = new Relationship();

        try {
            InstanceType instanceType = atlasRepositoryConnector.getRepositoryHelper().getNewInstanceType(
                    atlasRepositoryConnector.getRepositoryName(),
                    omrsRelationshipDef
            );
            relationship.setType(instanceType);
        } catch (TypeErrorException e) {
            throw new RepositoryErrorException(ApacheAtlasOMRSErrorCode.INVALID_INSTANCE.getMessageDefinition(methodName, omrsRelationshipDef.getName()),
                    RelationshipMapping.class.getName(),
                    methodName,
                    e);
        }

        return relationship;

    }

    /**
     * Throw a RepositoryErrorException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any parameters for formatting the error message
     * @throws RepositoryErrorException always
     */
    private void raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws RepositoryErrorException {
        throw new RepositoryErrorException(errorCode.getMessageDefinition(params),
                RelationshipMapping.class.getName(),
                methodName,
                cause);
    }

}
