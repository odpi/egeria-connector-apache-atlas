/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.instance.*;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSRepositoryConnector;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model.AtlasGuid;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.AttributeTypeDefStore;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefAttribute;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Class that generically handles converting an Apache Atlas EntityInstance object into an OMRS EntityDetail object.
 */
public class EntityMappingAtlas2OMRS {

    private static final Logger log = LoggerFactory.getLogger(EntityMappingAtlas2OMRS.class);

    private ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector;
    private TypeDefStore typeDefStore;
    private AttributeTypeDefStore attributeDefStore;
    private AtlasEntity.AtlasEntityWithExtInfo atlasEntityWithExtInfo;
    private AtlasEntity atlasEntity;
    private String prefix;
    private String userId;

    /**
     * Mapping itself must be initialized with various objects.
     *
     * @param atlasRepositoryConnector connectivity to an Apache Atlas repository
     * @param typeDefStore the store of mapped TypeDefs for the Atlas repository
     * @param attributeDefStore the store of mapped AttributeTypeDefs for the Atlas repository
     * @param instance the Atlas entity to be mapped
     * @param prefix the prefix indicating a generated type (and GUID), or null if not generated
     * @param userId the user through which to do the mapping
     */
    public        EntityMappingAtlas2OMRS(ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector,
                                   TypeDefStore typeDefStore,
                                   AttributeTypeDefStore attributeDefStore,
                                   AtlasEntity.AtlasEntityWithExtInfo instance,
                                   String prefix,
                                   String userId) {
        this.atlasRepositoryConnector = atlasRepositoryConnector;
        this.typeDefStore = typeDefStore;
        this.attributeDefStore = attributeDefStore;
        this.atlasEntityWithExtInfo = instance;
        this.atlasEntity = atlasEntityWithExtInfo.getEntity();
        this.prefix = prefix;
        this.userId = userId;
    }

    /**
     * Retrieve the mapped OMRS EntitySummary from the Apache Atlas EntityInstance used to construct this mapping object.
     *
     * @return EntitySummary
     * @throws RepositoryErrorException when unable to retrieve the EntitySummary
     */
    public EntitySummary getEntitySummary() throws RepositoryErrorException {

        EntitySummary summary = null;
        String atlasTypeDefName = atlasEntity.getTypeName();
        EntityMapping mapping = getMappingForAtlasType(atlasTypeDefName);

        if (mapping != null) {
            String omrsTypeDefName = mapping.getOmrsTypeName();
            atlasTypeDefName = mapping.getAtlasTypeName();
            log.info("Found mapped type for Atlas type '{}' with prefix '{}': {}", atlasTypeDefName, prefix, omrsTypeDefName);
            summary = getSkeletonEntitySummary(omrsTypeDefName, prefix);
            if (summary != null) {
                addClassifications(summary);
            }
        } else {
            log.warn("No mapping defined from Atlas type '{}' with prefix '{}'", atlasTypeDefName, prefix);
        }

        return summary;

    }

    /**
     * Retrieve the mapped OMRS EntityDetail from the Apache Atlas EntityInstance used to construct this mapping object.
     *
     * @return EntityDetail
     * @throws RepositoryErrorException when unable to retrieve the EntityDetail
     */
    public EntityDetail getEntityDetail() throws RepositoryErrorException {

        final String methodName = "getEntityDetail";
        EntityDetail detail = null;
        String atlasTypeDefName = atlasEntity.getTypeName();
        EntityMapping mapping = getMappingForAtlasType(atlasTypeDefName);

        if (mapping != null) {
            String omrsTypeDefName = mapping.getOmrsTypeName();
            atlasTypeDefName = mapping.getAtlasTypeName();
            log.info("Found mapped type for Atlas type '{}' with prefix '{}': {}", atlasTypeDefName, prefix, omrsTypeDefName);

            // Create the basic skeleton
            detail = getSkeletonEntityDetail(omrsTypeDefName, prefix);

            // Then apply the instance-specific mapping
            if (detail != null) {

                InstanceProperties instanceProperties = new InstanceProperties();
                OMRSRepositoryHelper omrsRepositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
                String repositoryName = atlasRepositoryConnector.getRepositoryName();

                Map<String, TypeDefAttribute> omrsAttributeMap = typeDefStore.getAllTypeDefAttributesForName(omrsTypeDefName);

                // Iterate through the provided mappings to set an OMRS instance property for each one
                Map<String, String> atlasToOmrsProperties = typeDefStore.getPropertyMappingsForAtlasTypeDef(atlasTypeDefName, prefix);
                Map<String, Object> atlasProperties = atlasEntity.getAttributes();
                if (atlasProperties != null) {
                    Set<String> alreadyMapped = new HashSet<>();
                    for (Map.Entry<String, String> property : atlasToOmrsProperties.entrySet()) {
                        String atlasProperty = property.getKey();
                        String omrsProperty = property.getValue();
                        if (omrsAttributeMap.containsKey(omrsProperty)) {
                            TypeDefAttribute typeDefAttribute = omrsAttributeMap.get(omrsProperty);
                            instanceProperties = AttributeMapping.addPropertyToInstance(omrsRepositoryHelper,
                                    repositoryName,
                                    typeDefAttribute,
                                    instanceProperties,
                                    attributeDefStore,
                                    atlasProperties.get(atlasProperty),
                                    methodName);
                            if (instanceProperties.getPropertyValue(omrsProperty) != null) {
                                alreadyMapped.add(atlasProperty);
                            }
                        } else {
                            log.warn("No OMRS attribute {} defined for asset type {} -- skipping mapping.", omrsProperty, omrsTypeDefName);
                        }
                    }

                    // And map any other simple (non-relationship) properties that are not otherwise mapped into 'additionalProperties'
                    Map<String, String> additionalProperties = new HashMap<>();

                    Set<String> nonRelationshipSet = atlasProperties.keySet();

                    // Remove all of the already-mapped properties from our list of non-relationship properties
                    nonRelationshipSet.removeAll(alreadyMapped);

                    // Iterate through the remaining property names, and add them to a map
                    // Note that because 'additionalProperties' is a string-to-string map, we will just convert everything
                    // to strings (even arrays of values, we'll concatenate into a single string)
                    for (String propertyName : nonRelationshipSet) {
                        Object propertyValue = atlasProperties.get(propertyName);
                        if (propertyValue != null) {
                            additionalProperties.put(propertyName, propertyValue.toString());
                        }
                    }

                    // and finally setup the 'additionalProperties' attribute using this map
                    instanceProperties = omrsRepositoryHelper.addStringMapPropertyToInstance(
                            repositoryName,
                            instanceProperties,
                            "additionalProperties",
                            additionalProperties,
                            methodName
                    );
                }

                detail.setProperties(instanceProperties);

                // TODO: detail.setReplicatedBy();
                addClassifications(detail);

            }
        } else {
            log.warn("No mapping defined from Atlas type '{}' with prefix '{}'", atlasTypeDefName, prefix);
        }

        return detail;

    }

    /**
     * Retrieves relationships for this entity based on the provided criteria.
     *
     * @param relationshipTypeGUID the OMRS GUID of the relationship TypeDef to which to limit the results
     * @param fromRelationshipElement the starting element for multiple pages of relationships
     * @param sequencingProperty the property by which to order results (or null)
     * @param sequencingOrder the ordering sequence to use for ordering results
     * @param pageSize the number of results to include per page
     * @return {@code List<Relationship>}
     * @throws RepositoryErrorException when unable to retrieve the mapped Relationships
     */
    @SuppressWarnings("unchecked")
    public List<Relationship> getRelationships(String relationshipTypeGUID,
                                               int fromRelationshipElement,
                                               String sequencingProperty,
                                               SequencingOrder sequencingOrder,
                                               int pageSize) throws RepositoryErrorException {

        final String methodName = "getRelationships";
        List<Relationship> omrsRelationships = new ArrayList<>();
        //OMRSRepositoryHelper omrsRepositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
        String repositoryName = atlasRepositoryConnector.getRepositoryName();

        // We have all the relationships from Atlas (no way to limit the query), so we will iterate
        // through them all
        Map<String, Object> atlasRelationships = atlasEntity.getRelationshipAttributes();
        for (Map.Entry<String, Object> atlasRelationship : atlasRelationships.entrySet()) {

            // TODO: we could avoid iterating through all of the assignments
            //  if we store the mapping between relationship property name and type in the TypeDefStore
            //  (but presumably the expensive bit is retrieving all the relationships, which happens anyway...)
            List<LinkedHashMap<?,?>> relationshipAssignments = null;
            Object atlasRelationshipValue = atlasRelationship.getValue();
            if (atlasRelationshipValue instanceof List) {
                relationshipAssignments = (List<LinkedHashMap<?,?>>) atlasRelationshipValue;
            } else if (atlasRelationshipValue instanceof LinkedHashMap) {
                // Not sure why Atlas doesn't just use the Jackson derserialization feature that makes single values into
                // lists, and end up with everything as an AtlasRelatedObjectId, but they don't...
                relationshipAssignments = new ArrayList<>();
                relationshipAssignments.add((LinkedHashMap<?,?>) atlasRelationshipValue);
            }

            // Handle actual relationships in Atlas
            if (relationshipAssignments != null) {
                for (LinkedHashMap<?,?> valueToTranslate : relationshipAssignments) {

                    AtlasRelatedObjectId relationshipAssignment = new AtlasRelatedObjectId(valueToTranslate);
                    String atlasRelationshipType = relationshipAssignment.getRelationshipType();
                    String atlasRelationshipGuid = relationshipAssignment.getRelationshipGuid();

                    Map<String, String> omrsPrefixToType = typeDefStore.getMappedOMRSTypeDefNameWithPrefixes(atlasRelationshipType);

                    for (Map.Entry<String, String> entry : omrsPrefixToType.entrySet()) {

                        String relationshipPrefix = entry.getKey();
                        String omrsRelationshipType = entry.getValue();

                        TypeDef omrsTypeDef = typeDefStore.getTypeDefByName(omrsRelationshipType);
                        String omrsTypeDefGuid = omrsTypeDef.getGUID();

                        // Only include the relationship if we are including all or those that match this type GUID
                        if (relationshipTypeGUID == null || omrsTypeDefGuid.equals(relationshipTypeGUID)) {

                            try {
                                RelationshipMapping mapping = new RelationshipMapping(
                                        atlasRepositoryConnector,
                                        typeDefStore,
                                        attributeDefStore,
                                        new AtlasGuid(atlasRelationshipGuid, relationshipPrefix),
                                        atlasRepositoryConnector.getRelationshipByGUID(atlasRelationshipGuid),
                                        userId);

                                Relationship omrsRelationship = mapping.getRelationship();
                                if (omrsRelationship != null) {
                                    omrsRelationships.add(omrsRelationship);
                                }
                            } catch (AtlasServiceException e) {
                                raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, e, atlasRelationshipGuid, methodName, repositoryName);
                            }

                        }
                    }
                }
            }

        }

        // Then handle any generated relationships (between what is the same entity in Atlas but different entities in OMRS)
        if (relationshipTypeGUID == null) {
            Map<String, TypeDefStore.EndpointMapping> mappedRelationships = typeDefStore.getAllEndpointMappingsFromAtlasName(atlasEntity.getTypeName());
            for (Map.Entry<String, TypeDefStore.EndpointMapping> entry : mappedRelationships.entrySet()) {
                String relationshipPrefix = entry.getKey();
                TypeDefStore.EndpointMapping mapping = entry.getValue();
                // Only generate the generated relationships (normally-mapped should be covered already above)
                if (relationshipPrefix != null) {
                    // TODO: assumes that all generated relationships have the same Atlas entity on both ends
                    AtlasGuid atlasGuid = new AtlasGuid(atlasEntity.getGuid(), relationshipPrefix);
                    Relationship omrsRelationship = RelationshipMapping.getSelfReferencingRelationship(
                            atlasRepositoryConnector,
                            typeDefStore,
                            atlasGuid,
                            atlasEntity
                    );
                    if (omrsRelationship != null) {
                        omrsRelationships.add(omrsRelationship);
                    } else {
                        raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, null, atlasGuid.toString(), methodName, repositoryName);
                    }
                }
            }
        } else {
            TypeDef typeDef = typeDefStore.getTypeDefByGUID(relationshipTypeGUID);
            if (typeDef != null) {
                String omrsTypeDefName = typeDef.getName();
                Map<String, Set<String>> atlasTypesByPrefix = typeDefStore.getAllMappedAtlasTypeDefNames(omrsTypeDefName);
                for (Map.Entry<String, Set<String>> entry : atlasTypesByPrefix.entrySet()) {
                    String prefixForType = entry.getKey();
                    Set<String> atlasTypeNames = entry.getValue();
                    // TODO: Only generate the generated relationships (normally-mapped should be covered already above)
                    if (prefixForType != null) {
                        log.info("Have not yet implemented this relationship: ({}) {}", prefixForType, atlasTypeNames);
                    }
                }
            }
        }

        // Now sort the results, if requested
        Comparator<Relationship> comparator = SequencingUtils.getRelationshipComparator(sequencingOrder, sequencingProperty);
        if (comparator != null) {
            omrsRelationships.sort(comparator);
        }

        // And finally limit the results, if requested
        int endOfPageMarker = Math.min(fromRelationshipElement + pageSize, omrsRelationships.size());
        if (fromRelationshipElement != 0 || endOfPageMarker < omrsRelationships.size()) {
            omrsRelationships = omrsRelationships.subList(fromRelationshipElement, endOfPageMarker);
        }

        return (omrsRelationships.isEmpty() ? null : omrsRelationships);

    }

    /**
     * Attempt to resolve the Atlas entity to a mapping, using the most granular mapping we can find. If there is not
     * a mapping for the Atlas entity type directly, traverse up the inheritance hierarchy to find the lowest level
     * we can for the type that still has a mapping (even if this means we eventually only map it as a Referenceable).
     *
     * @param atlasTypeDefName the name of the Apache Atlas type def for which to find the mapping
     * @return String giving the OMRS type name of the lowest-level mapping we can find
     */
    private EntityMapping getMappingForAtlasType(String atlasTypeDefName) {
        if (atlasTypeDefName != null) {
            String omrsTypeDefName = typeDefStore.getMappedOMRSTypeDefName(atlasTypeDefName, prefix);
            if (omrsTypeDefName == null) {
                String parent = atlasRepositoryConnector.getParentTypeForAtlasEntityType(atlasTypeDefName);
                return getMappingForAtlasType(parent);
            } else {
                return new EntityMapping(atlasTypeDefName, omrsTypeDefName);
            }
        } else {
            return null;
        }
    }

    /**
     * Create the base skeleton of an EntitySummary, irrespective of the specific Apache Atlas object.
     *
     * @param omrsTypeDefName the name of the OMRS TypeDef for which to create a skeleton EntitySummary
     * @param prefix the prefix for a generated entity (if any)
     * @return EntitySummary
     */
    private EntitySummary getSkeletonEntitySummary(String omrsTypeDefName, String prefix) {

        EntitySummary summary = null;
        try {
            summary = atlasRepositoryConnector.getRepositoryHelper().getSkeletonEntitySummary(
                    atlasRepositoryConnector.getRepositoryName(),
                    atlasRepositoryConnector.getMetadataCollectionId(),
                    InstanceProvenanceType.LOCAL_COHORT,
                    userId,
                    omrsTypeDefName
            );
            summary.setMetadataCollectionName(atlasRepositoryConnector.getMetadataCollectionName());
            String guid = atlasEntity.getGuid();
            AtlasGuid atlasGuid = new AtlasGuid(guid, prefix);
            summary.setGUID(atlasGuid.toString());
            summary.setInstanceURL(getInstanceURL(guid));
            setModAndVersionDetails(summary);
        } catch (TypeErrorException e) {
            log.error("Unable to get skeleton summary entity.", e);
        }

        return summary;

    }

    /**
     * Create the base skeleton of an EntityDetail, irrespective of the specific Apache Atlas object.
     *
     * @param omrsTypeDefName the name of the OMRS TypeDef for which to create a skeleton EntityDetail
     * @param prefix the prefix for a generated entity (if any)
     * @return EntityDetail
     */
    private EntityDetail getSkeletonEntityDetail(String omrsTypeDefName, String prefix) {

        EntityDetail detail = null;
        try {
            detail = atlasRepositoryConnector.getRepositoryHelper().getSkeletonEntity(
                    atlasRepositoryConnector.getRepositoryName(),
                    atlasRepositoryConnector.getMetadataCollectionId(),
                    InstanceProvenanceType.LOCAL_COHORT,
                    userId,
                    omrsTypeDefName
            );
            detail.setMetadataCollectionName(atlasRepositoryConnector.getMetadataCollectionName());
            switch(atlasEntity.getStatus()) {
                case ACTIVE:
                    detail.setStatus(InstanceStatus.ACTIVE);
                    break;
                case DELETED:
                    detail.setStatus(InstanceStatus.DELETED);
                    break;
                default:
                    log.warn("Unhandled status: {}", atlasEntity.getStatus());
                    break;
            }
            String guid = atlasEntity.getGuid();
            AtlasGuid atlasGuid = new AtlasGuid(guid, prefix);
            detail.setGUID(atlasGuid.toString());
            detail.setInstanceURL(getInstanceURL(guid));
            setModAndVersionDetails(detail);
        } catch (TypeErrorException e) {
            log.error("Unable to get skeleton detail entity.", e);
        }

        return detail;

    }

    /**
     * Retrieve an API-accessible instance URL based on the GUID of an entity.
     *
     * @param guid the guid of the entity
     * @return String
     */
    private String getInstanceURL(String guid) {
        return atlasRepositoryConnector.getBaseURL() + ApacheAtlasOMRSRepositoryConnector.EP_ENTITY + guid;
    }

    /**
     * Set the creation, modification and version details of the object.
     *
     * @param omrsObj the OMRS object (EntitySummary or EntityDetail)
     */
    private void setModAndVersionDetails(EntitySummary omrsObj) {
        omrsObj.setCreatedBy(atlasEntity.getCreatedBy());
        omrsObj.setCreateTime(atlasEntity.getCreateTime());
        omrsObj.setUpdatedBy(atlasEntity.getUpdatedBy());
        omrsObj.setUpdateTime(atlasEntity.getUpdateTime());
        omrsObj.setVersion(atlasEntity.getVersion());
    }

    /**
     * Add any classifications: since Atlas does not come with any pre-defined Classifications we will assume
     * that any that exist are OMRS-created and therefore are one-to-one mappings to OMRS classifications
     * (but we will check that the classification is a known OMRS classification before proceeding)
     *
     * @param omrsObj the OMRS object (EntitySummary or EntityDetail)
     * @throws RepositoryErrorException when unable to add the classifications
     */
    private void addClassifications(EntitySummary omrsObj) throws RepositoryErrorException {

        final String methodName = "addClassifications";

        List<Classification> classifications = new ArrayList<>();
        List<AtlasClassification> classificationAssignments = atlasEntity.getClassifications();
        if (classificationAssignments != null) {

            OMRSRepositoryHelper omrsRepositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
            String repositoryName = atlasRepositoryConnector.getRepositoryName();
            String atlasTypeDefName = atlasEntity.getTypeName();
            String omrsTypeDefName = typeDefStore.getMappedOMRSTypeDefName(atlasTypeDefName, prefix);

            for (AtlasClassification classificationAssignment : classificationAssignments) {

                String atlasClassificationName = classificationAssignment.getTypeName();
                // Remember that the atlasClassificationName == the omrsClassificationName
                TypeDef classificationDef = typeDefStore.getTypeDefByName(atlasClassificationName);

                if (classificationDef != null) {
                    Map<String, Object> atlasClassificationProperties = classificationAssignment.getAttributes();
                    Map<String, TypeDefAttribute> classificationAttributeMap = typeDefStore.getAllTypeDefAttributesForName(atlasClassificationName);
                    InstanceProperties omrsClassificationProperties = new InstanceProperties();
                    if (atlasClassificationProperties != null) {

                        for (Map.Entry<String, Object> property : atlasClassificationProperties.entrySet()) {
                            String propertyName = property.getKey();
                            Object propertyValue = property.getValue();
                            if (classificationAttributeMap.containsKey(propertyName)) {
                                TypeDefAttribute typeDefAttribute = classificationAttributeMap.get(propertyName);
                                omrsClassificationProperties = AttributeMapping.addPropertyToInstance(omrsRepositoryHelper,
                                        repositoryName,
                                        typeDefAttribute,
                                        omrsClassificationProperties,
                                        attributeDefStore,
                                        propertyValue,
                                        methodName);
                            } else {
                                log.warn("No OMRS attribute {} defined for asset type {} -- skipping mapping.", propertyName, atlasClassificationName);
                            }
                        }

                    }
                    try {
                        // TODO: currently hard-coded to ASSIGNED classifications, need to also handle PROPAGATED
                        Classification classification = atlasRepositoryConnector.getRepositoryHelper().getNewClassification(
                                repositoryName,
                                userId,
                                atlasClassificationName,
                                omrsTypeDefName,
                                ClassificationOrigin.ASSIGNED,
                                null,
                                omrsClassificationProperties
                        );
                        classification.setMetadataCollectionId(atlasRepositoryConnector.getMetadataCollectionId());
                        classification.setMetadataCollectionName(atlasRepositoryConnector.getMetadataCollectionName());
                        classification.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
                        // Setting classification mod details based on the overall entity (nothing more fine-grained in Atlas)
                        classification.setCreatedBy(omrsObj.getCreatedBy());
                        classification.setCreateTime(omrsObj.getCreateTime());
                        classification.setUpdatedBy(omrsObj.getUpdatedBy());
                        classification.setUpdateTime(omrsObj.getUpdateTime());
                        classification.setVersion(omrsObj.getUpdateTime().getTime());
                        classifications.add(classification);
                    } catch (TypeErrorException e) {
                        raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.INVALID_CLASSIFICATION_FOR_ENTITY, methodName, e, atlasClassificationName, omrsTypeDefName);
                    }
                } else {
                    log.warn("Classification {} unknown to repository -- skipping.", atlasClassificationName);
                }
            }
        }
        if (!classifications.isEmpty()) {
            omrsObj.setClassifications(classifications);
        }

    }

    /**
     * For translating between entity representations.
     */
    private final class EntityMapping {

        private String atlasTypeName;
        private String omrsTypeName;

        EntityMapping(String atlasTypeName,
                      String omrsTypeName) {
            this.atlasTypeName = atlasTypeName;
            this.omrsTypeName = omrsTypeName;
        }

        public String getAtlasTypeName() { return atlasTypeName; }
        public String getOmrsTypeName() { return omrsTypeName; }

        @Override
        public String toString() {
            return "EntityMapping{" +
                    "atlasTypeName='" + atlasTypeName + '\'' +
                    ", omrsTypeName='" + omrsTypeName + '\'' +
                    '}';
        }
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
        if (cause == null) {
            throw new RepositoryErrorException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new RepositoryErrorException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

}
