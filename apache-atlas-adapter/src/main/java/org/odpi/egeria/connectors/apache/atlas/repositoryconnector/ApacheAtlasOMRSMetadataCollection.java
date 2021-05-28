/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.discovery.AtlasSearchResult;
import org.apache.atlas.model.discovery.SearchParameters;
import org.apache.atlas.model.instance.*;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.eventmapper.ApacheAtlasOMRSRepositoryEventMapper;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping.*;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model.AtlasGuid;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.AttributeTypeDefStore;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollectionBase;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryValidator;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class ApacheAtlasOMRSMetadataCollection extends OMRSMetadataCollectionBase {

    private static final Logger log = LoggerFactory.getLogger(ApacheAtlasOMRSMetadataCollection.class);

    private final SimpleDateFormat atlasDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector;
    private TypeDefStore typeDefStore;
    private AttributeTypeDefStore attributeTypeDefStore;
    private Set<InstanceStatus> availableStates;
    private ApacheAtlasOMRSRepositoryEventMapper eventMapper = null;

    /**
     * @param parentConnector      connector that this metadata collection supports.
     *                             The connector has the information to call the metadata repository.
     * @param repositoryName       name of this repository.
     * @param repositoryHelper     helper that provides methods to repository connectors and repository event mappers
     *                             to build valid type definitions (TypeDefs), entities and relationships.
     * @param repositoryValidator  validator class for checking open metadata repository objects and parameters
     * @param metadataCollectionId unique identifier for the repository
     */
    public ApacheAtlasOMRSMetadataCollection(ApacheAtlasOMRSRepositoryConnector parentConnector,
                                             String repositoryName,
                                             OMRSRepositoryHelper repositoryHelper,
                                             OMRSRepositoryValidator repositoryValidator,
                                             String metadataCollectionId) {
        super(parentConnector, repositoryName, repositoryHelper, repositoryValidator, metadataCollectionId);
        parentConnector.setRepositoryName(repositoryName);
        this.atlasRepositoryConnector = parentConnector;
        this.typeDefStore = new TypeDefStore();
        this.attributeTypeDefStore = new AttributeTypeDefStore();
        this.availableStates = new HashSet<>();
        availableStates.add(InstanceStatus.ACTIVE);
        availableStates.add(InstanceStatus.DELETED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDefGallery getAllTypes(String userId) throws
            RepositoryErrorException,
            InvalidParameterException {

        final String methodName = "getAllTypes";
        super.basicRequestValidation(userId, methodName);

        TypeDefGallery typeDefGallery = new TypeDefGallery();
        List<TypeDef> typeDefs = typeDefStore.getAllTypeDefs();
        log.debug("Retrieved {} implemented TypeDefs for this repository.", typeDefs.size());
        typeDefGallery.setTypeDefs(typeDefs);

        List<AttributeTypeDef> attributeTypeDefs = attributeTypeDefStore.getAllAttributeTypeDefs();
        log.debug("Retrieved {} implemented AttributeTypeDefs for this repository.", attributeTypeDefs.size());
        typeDefGallery.setAttributeTypeDefs(attributeTypeDefs);

        return typeDefGallery;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TypeDef> findTypeDefsByCategory(String userId,
                                                TypeDefCategory category) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "findTypeDefsByCategory";
        final String categoryParameterName = "category";
        super.typeDefCategoryParameterValidation(userId, category, categoryParameterName, methodName);

        List<TypeDef> typeDefs = new ArrayList<>();
        for (TypeDef candidate : typeDefStore.getAllTypeDefs()) {
            if (candidate.getCategory().equals(category)) {
                typeDefs.add(candidate);
            }
        }
        return typeDefs;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AttributeTypeDef> findAttributeTypeDefsByCategory(String userId,
                                                                  AttributeTypeDefCategory category) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "findAttributeTypeDefsByCategory";
        final String categoryParameterName = "category";
        super.attributeTypeDefCategoryParameterValidation(userId, category, categoryParameterName, methodName);

        List<AttributeTypeDef> typeDefs = new ArrayList<>();
        for (AttributeTypeDef candidate : attributeTypeDefStore.getAllAttributeTypeDefs()) {
            if (candidate.getCategory().equals(category)) {
                typeDefs.add(candidate);
            }
        }
        return typeDefs;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TypeDef> findTypeDefsByProperty(String userId,
                                                TypeDefProperties matchCriteria) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "findTypeDefsByProperty";
        final String matchCriteriaParameterName = "matchCriteria";
        super.typeDefPropertyParameterValidation(userId, matchCriteria, matchCriteriaParameterName, methodName);

        List<TypeDef> typeDefs = typeDefStore.getAllTypeDefs();
        List<TypeDef> results = new ArrayList<>();
        if (matchCriteria != null) {
            Map<String, Object> properties = matchCriteria.getTypeDefProperties();
            for (TypeDef candidate : typeDefs) {
                List<TypeDefAttribute> candidateProperties = candidate.getPropertiesDefinition();
                if (candidateProperties != null) {
                    for (TypeDefAttribute candidateAttribute : candidateProperties) {
                        String candidateName = candidateAttribute.getAttributeName();
                        if (properties.containsKey(candidateName)) {
                            results.add(candidate);
                        }
                    }
                }
            }
        } else {
            results = typeDefs;
        }

        return results;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TypeDef> searchForTypeDefs(String userId,
                                           String searchCriteria) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "searchForTypeDefs";
        final String searchCriteriaParameterName = "searchCriteria";
        super.typeDefSearchParameterValidation(userId, searchCriteria, searchCriteriaParameterName, methodName);

        List<TypeDef> typeDefs = new ArrayList<>();
        for (TypeDef candidate : typeDefStore.getAllTypeDefs()) {
            if (candidate.getName().matches(searchCriteria)) {
                typeDefs.add(candidate);
            }
        }
        return typeDefs;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDef getTypeDefByGUID(String userId,
                                    String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException {

        final String methodName = "getTypeDefByGUID";
        final String guidParameterName = "guid";
        super.typeGUIDParameterValidation(userId, guid, guidParameterName, methodName);

        TypeDef found = typeDefStore.getTypeDefByGUID(guid);

        if (found == null) {
            raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, guid, repositoryName);
        }

        return found;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeTypeDef getAttributeTypeDefByGUID(String userId,
                                                       String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException {

        final String methodName = "getAttributeTypeDefByGUID";
        final String guidParameterName = "guid";
        super.typeGUIDParameterValidation(userId, guid, guidParameterName, methodName);

        AttributeTypeDef found = attributeTypeDefStore.getAttributeTypeDefByGUID(guid);
        if (found == null) {
            raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, guid, repositoryName);
        }
        return found;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDef getTypeDefByName(String userId,
                                    String name) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException {

        final String methodName = "getTypeDefByName";
        final String nameParameterName = "name";
        super.typeNameParameterValidation(userId, name, nameParameterName, methodName);

        TypeDef found = typeDefStore.getTypeDefByName(name);

        if (found == null) {
            raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, name, repositoryName);
        }

        return found;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeTypeDef getAttributeTypeDefByName(String userId,
                                                       String name) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException {

        final String methodName = "getAttributeTypeDefByName";
        final String nameParameterName = "name";
        super.typeNameParameterValidation(userId, name, nameParameterName, methodName);

        AttributeTypeDef found = attributeTypeDefStore.getAttributeTypeDefByName(name);
        if (found == null) {
            raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, name, repositoryName);
        }
        return found;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTypeDef(String userId,
                           TypeDef newTypeDef) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotSupportedException,
            TypeDefKnownException,
            TypeDefConflictException,
            InvalidTypeDefException {

        final String methodName = "addTypeDef";
        final String typeDefParameterName = "newTypeDef";
        super.newTypeDefParameterValidation(userId, newTypeDef, typeDefParameterName, methodName);

        TypeDefCategory typeDefCategory = newTypeDef.getCategory();
        String omrsTypeDefName = newTypeDef.getName();
        log.debug("Looking for mapping for {} of type {}", omrsTypeDefName, typeDefCategory.getName());

        if (typeDefStore.isTypeDefMapped(omrsTypeDefName)) {

            /* If it is a mapped TypeDef, retrieve the mapped type from Atlas and validate it covers what we require
            // (no longer needed for a read-only connector)
            String atlasTypeName = typeDefStore.getMappedAtlasTypeDefName(omrsTypeDefName);
            List<String> gaps = validateTypeDefCoverage(newTypeDef, atlasRepositoryConnector.getTypeDefByName(atlasTypeName, newTypeDef.getCategory()));
            if (gaps != null) {
                // If there were gaps, drop the typedef as unimplemented
                typeDefStore.addUnimplementedTypeDef(newTypeDef);
                throw new TypeDefNotSupportedException(
                        404,
                        ApacheAtlasOMRSMetadataCollection.class.getName(),
                        methodName,
                        omrsTypeDefName + " is not supported.",
                        String.join(", ", gaps),
                        "Request support through Egeria GitHub issue."
                );
            } else {
                // Otherwise add it as implemented
                typeDefStore.addTypeDef(newTypeDef);
            }
            */

            typeDefStore.addTypeDef(newTypeDef);

        } else if (typeDefStore.isUnimplementedSupertype(omrsTypeDefName)) {

            // If it's one that we will ultimately inherit from, we should store it as a type
            // (to support its various properties later on, particularly for updates to the super-types)
            typeDefStore.addTypeDef(newTypeDef);

        } else if (!typeDefStore.isReserved(omrsTypeDefName)) {

            if (atlasRepositoryConnector.typeDefExistsByName(omrsTypeDefName)) {
                /* If the TypeDef already exists in Atlas, add it to our store
                List<String> gaps = validateTypeDefCoverage(newTypeDef, atlasRepositoryConnector.getTypeDefByName(omrsTypeDefName, newTypeDef.getCategory()));
                if (gaps != null) {
                    // If there were gaps, drop the typedef as unimplemented
                    typeDefStore.addUnimplementedTypeDef(newTypeDef);
                    throw new TypeDefNotSupportedException(
                            404,
                            ApacheAtlasOMRSMetadataCollection.class.getName(),
                            methodName,
                            omrsTypeDefName + " is not supported.",
                            String.join(", ", gaps),
                            "Request support through Egeria GitHub issue."
                    );
                } else {
                    // Otherwise add it as implemented
                    typeDefStore.addTypeDef(newTypeDef);
                }
                */
                typeDefStore.addTypeDef(newTypeDef);
            } else {
                switch(newTypeDef.getCategory()) {
                    /*case ENTITY_DEF:
                        EntityDefMapping.addEntityTypeToAtlas(
                                (EntityDef) newTypeDef,
                                typeDefStore,
                                attributeTypeDefStore,
                                atlasRepositoryConnector
                        );
                        break;*/
                    // For now, only create classifications and relationships (no new entity types)
                    case RELATIONSHIP_DEF:
                        RelationshipDefMapping.addRelationshipTypeToAtlas(
                                (RelationshipDef) newTypeDef,
                                typeDefStore,
                                attributeTypeDefStore,
                                atlasRepositoryConnector
                        );
                        break;
                    case CLASSIFICATION_DEF:
                        ClassificationDefMapping.addClassificationTypeToAtlas(
                                (ClassificationDef) newTypeDef,
                                typeDefStore,
                                attributeTypeDefStore,
                                atlasRepositoryConnector
                        );
                        break;
                    default:
                        typeDefStore.addUnimplementedTypeDef(newTypeDef);
                        raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, repositoryName);
                }
            }

        } else {
            // Otherwise, we'll drop it as unimplemented
            typeDefStore.addUnimplementedTypeDef(newTypeDef);
            raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, repositoryName);
        }

        if (eventMapper != null) {
            eventMapper.sendNewTypeDefEvent(newTypeDef);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public  void addAttributeTypeDef(String             userId,
                                     AttributeTypeDef   newAttributeTypeDef) throws InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotSupportedException,
            TypeDefKnownException,
            TypeDefConflictException,
            InvalidTypeDefException {

        final String  methodName           = "addAttributeTypeDef";
        final String  typeDefParameterName = "newAttributeTypeDef";

        /*
         * Validate parameters
         */
        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateAttributeTypeDef(repositoryName, typeDefParameterName, newAttributeTypeDef, methodName);
        repositoryValidator.validateUnknownAttributeTypeDef(repositoryName, typeDefParameterName, newAttributeTypeDef, methodName);

        // Note this is only implemented for Enums, support for other types is indicated directly
        // in the verifyAttributeTypeDef method
        AttributeTypeDefCategory attributeTypeDefCategory = newAttributeTypeDef.getCategory();
        String omrsTypeDefName = newAttributeTypeDef.getName();
        log.debug("Looking for mapping for {} of type {}", omrsTypeDefName, attributeTypeDefCategory.getName());

        if (attributeTypeDefStore.isTypeDefMapped(omrsTypeDefName)) {

            // If it is a mapped TypeDef, add it to our store
            attributeTypeDefStore.addTypeDef(newAttributeTypeDef);

        } else if (newAttributeTypeDef.getCategory().equals(AttributeTypeDefCategory.ENUM_DEF)) {

            if (atlasRepositoryConnector.typeDefExistsByName(omrsTypeDefName)) {
                // If the TypeDef already exists in Atlas, add it to our store
                // TODO: should really still verify it, in case TypeDef changes
                attributeTypeDefStore.addTypeDef(newAttributeTypeDef);
            } else {
                // Otherwise, if it is a Classification, we'll add it to Atlas itself
                EnumDefMapping.addEnumToAtlas(
                        (EnumDef) newAttributeTypeDef,
                        attributeTypeDefStore,
                        atlasRepositoryConnector
                );
            }

        } else {
            // Otherwise, we'll drop it as unimplemented
            attributeTypeDefStore.addUnimplementedTypeDef(newAttributeTypeDef);
            raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, repositoryName);
        }

        if (eventMapper != null) {
            eventMapper.sendNewAttributeTypeDefEvent(newAttributeTypeDef);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyTypeDef(String  userId,
                                 TypeDef typeDef) throws InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotSupportedException,
            InvalidTypeDefException {

        final String  methodName           = "verifyTypeDef";
        final String  typeDefParameterName = "typeDef";

        /*
         * Validate parameters
         */
        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);
        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateTypeDef(repositoryName, typeDefParameterName, typeDef, methodName);

        String guid = typeDef.getGUID();

        // If we know the TypeDef is unimplemented, immediately throw an exception stating as much
        if (typeDefStore.getUnimplementedTypeDefByGUID(guid) != null) {
            raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, typeDef.getName(), repositoryName);
        }
        /*} else if (typeDefStore.getTypeDefByGUID(guid) != null) {

            // For a read-only connector, leave the status validation to when we try to write a status (throw exception at that point if we cannot support it)
            List<String> issues = new ArrayList<>();
            boolean bVerified = true;

            Set<InstanceStatus> validStatuses = new HashSet<>(typeDef.getValidInstanceStatusList());
            boolean bVerified = validStatuses.equals(availableStates);
            if (!bVerified) {
                issues.add("not all statuses supported: " + validStatuses);
            } */

            /* For a read-only connector, we do not need to support all properties
            // Validate that we support all of the possible properties before deciding whether we
            // fully-support the TypeDef or not
            String omrsTypeDefName = typeDef.getName();
            List<TypeDefAttribute> properties = typeDef.getPropertiesDefinition();
            if (properties != null) {
                Map<String, String> mappedProperties = typeDefStore.getPropertyMappingsForOMRSTypeDef(omrsTypeDefName);
                for (TypeDefAttribute typeDefAttribute : properties) {
                    String omrsPropertyName = typeDefAttribute.getAttributeName();
                    if (!mappedProperties.containsKey(omrsPropertyName)) {
                        bVerified = false;
                        issues.add("property '" + omrsPropertyName + "' is not mapped");
                    }
                }
            }

            // If we were unable to verify everything, throw exception indicating it is not a supported TypeDef
            if (!bVerified) {
                log.warn("TypeDef '{}' cannot be supported due to conflicts: {}", typeDef.getName(), String.join(", ", issues));
                OMRSErrorCode errorCode = OMRSErrorCode.VERIFY_CONFLICT_DETECTED;
                String        errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(guid,
                        methodName,
                        repositoryName);
                throw new TypeDefConflictException(errorCode.getHTTPErrorCode(),
                        this.getClass().getName(),
                        methodName,
                        errorMessage,
                        errorCode.getSystemAction(),
                        errorCode.getUserAction());
            } else {
                return true;
            }

            } else {
                // It is completely unknown to us, so go ahead and try to addTypeDef
                return false;
            }
        }*/

        return typeDefStore.getTypeDefByGUID(guid) != null;

    }

    /**
     * Verify that a definition of an AttributeTypeDef is either new or matches the definition already stored.
     *
     * @param userId unique identifier for requesting user.
     * @param attributeTypeDef TypeDef structure describing the TypeDef to test.
     * @return boolean where true means the TypeDef matches the local definition where false means the TypeDef is not known.
     * @throws InvalidParameterException the TypeDef is null.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                  the metadata collection is stored.
     * @throws TypeDefNotSupportedException the repository is not able to support this TypeDef.
     * @throws TypeDefConflictException the new TypeDef conflicts with an existing TypeDef.
     * @throws InvalidTypeDefException the new TypeDef has invalid contents.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.
     */
    @Override
    public boolean verifyAttributeTypeDef(String            userId,
                                          AttributeTypeDef  attributeTypeDef) throws InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotSupportedException,
            TypeDefConflictException,
            InvalidTypeDefException,
            UserNotAuthorizedException {

        final String  methodName           = "verifyAttributeTypeDef";
        final String  typeDefParameterName = "attributeTypeDef";

        /*
         * Validate parameters
         */
        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        repositoryValidator.validateUserId(repositoryName, userId, methodName);
        repositoryValidator.validateAttributeTypeDef(repositoryName, typeDefParameterName, attributeTypeDef, methodName);

        boolean bImplemented;
        switch (attributeTypeDef.getCategory()) {
            case PRIMITIVE:
            case COLLECTION:
                bImplemented = true;
                break;
            case ENUM_DEF:
                bImplemented = attributeTypeDefStore.isTypeDefMapped(attributeTypeDef.getName());
                break;
            default:
                bImplemented = false;
                break;
        }

        return bImplemented;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDef updateTypeDef(String userId, TypeDefPatch typeDefPatch) throws
            InvalidParameterException,
            RepositoryErrorException,
            TypeDefNotKnownException,
            PatchErrorException,
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "updateTypeDef";
        super.updateTypeDefParameterValidation(userId, typeDefPatch, methodName);

        String omrsTypeDefName = typeDefPatch.getTypeDefName();
        String omrsTypeDefGUID = typeDefPatch.getTypeDefGUID();
        TypeDef existing = typeDefStore.getTypeDefByGUID(omrsTypeDefGUID);
        TypeDef revised = null;

        if (existing != null) {
            log.info("Updating TypeDef '{}' based on patch.", omrsTypeDefName);
            revised = repositoryHelper.applyPatch(atlasRepositoryConnector.getServerName(), existing, typeDefPatch);
            // TODO: update the Atlas type itself first?
            switch(revised.getCategory()) {
                case ENTITY_DEF:
                    // For a mapped type (entity) that already exists, all we need to do is update the stored
                    // TypeDef itself -- the mapping will not change for read-only connectivity
                    break;
                case RELATIONSHIP_DEF:
                    RelationshipDefMapping.updateRelationshipTypeInAtlas(
                            (RelationshipDef) revised,
                            typeDefStore,
                            attributeTypeDefStore,
                            atlasRepositoryConnector
                    );
                    break;
                case CLASSIFICATION_DEF:
                    ClassificationDefMapping.updateClassificationTypeInAtlas(
                            (ClassificationDef) revised,
                            typeDefStore,
                            attributeTypeDefStore,
                            atlasRepositoryConnector
                    );
                    break;
                default:
                    typeDefStore.addUnimplementedTypeDef(revised);
                    raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, repositoryName);
            }
            typeDefStore.addTypeDef(revised);
            if (eventMapper != null) {
                eventMapper.sendUpdatedTypeDefEvent(typeDefPatch);
            }
        } else {
            // Still need to update unimplemented TypeDefs so that we have the latest for supertypes
            log.info("Updating unmapped TypeDef '{}' based on patch.", omrsTypeDefName);
            TypeDef unimplemented = typeDefStore.getUnimplementedTypeDefByGUID(omrsTypeDefGUID);
            if (unimplemented != null) {
                revised = repositoryHelper.applyPatch(atlasRepositoryConnector.getServerName(), unimplemented, typeDefPatch);
            }
            raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, repositoryName);
        }

        return revised;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail isEntityKnown(String userId,
                                      String guid) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "isEntityKnown";
        super.getInstanceParameterValidation(userId, guid, methodName);

        EntityDetail detail = null;
        try {
            detail = getEntityDetail(userId, guid);
        } catch (EntityNotKnownException e) {
            log.info("Entity {} not known to the repository, or only a proxy.", guid, e);
        }
        return detail;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitySummary getEntitySummary(String userId,
                                          String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {

        final String methodName = "getEntitySummary";
        super.getInstanceParameterValidation(userId, guid, methodName);

        // Guid cannot be null here, as validation above ensures it is non-null
        AtlasGuid atlasGuid = AtlasGuid.fromGuid(guid);
        String prefix = atlasGuid.getGeneratedPrefix();
        AtlasEntity.AtlasEntityWithExtInfo entity = getAtlasEntitySafe(atlasGuid.getAtlasGuid(), methodName);
        EntityMappingAtlas2OMRS mapping = new EntityMappingAtlas2OMRS(atlasRepositoryConnector, typeDefStore, attributeTypeDefStore, entity, prefix, userId);
        return mapping.getEntitySummary();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityDetail getEntityDetail(String userId,
                                        String guid) throws
            InvalidParameterException,
            RepositoryErrorException,
            EntityNotKnownException {

        final String methodName = "getEntityDetail";
        super.getInstanceParameterValidation(userId, guid, methodName);

        // Guid cannot be null here, as validation above ensures it is non-null
        AtlasGuid atlasGuid = AtlasGuid.fromGuid(guid);
        String prefix = atlasGuid.getGeneratedPrefix();
        AtlasEntity.AtlasEntityWithExtInfo entity = getAtlasEntitySafe(atlasGuid.getAtlasGuid(), methodName);
        EntityMappingAtlas2OMRS mapping = new EntityMappingAtlas2OMRS(atlasRepositoryConnector, typeDefStore, attributeTypeDefStore, entity, prefix, userId);
        return mapping.getEntityDetail();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Relationship> getRelationshipsForEntity(String userId,
                                                        String entityGUID,
                                                        String relationshipTypeGUID,
                                                        int fromRelationshipElement,
                                                        List<InstanceStatus> limitResultsByStatus,
                                                        Date asOfTime,
                                                        String sequencingProperty,
                                                        SequencingOrder sequencingOrder,
                                                        int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            EntityNotKnownException,
            PagingErrorException,
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        final String  methodName = "getRelationshipsForEntity";
        getRelationshipsForEntityParameterValidation(
                userId,
                entityGUID,
                relationshipTypeGUID,
                fromRelationshipElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize
        );

        List<Relationship> alRelationships = null;

        // Immediately throw unimplemented exception if trying to retrieve historical view or sequence by property
        if (asOfTime != null) {
            raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode.NO_HISTORY, methodName, repositoryName);
        } else if (limitResultsByStatus == null
                || (limitResultsByStatus.size() == 1 && limitResultsByStatus.contains(InstanceStatus.ACTIVE))) {

            // Otherwise, only bother searching if we are after ACTIVE (or "all") entities -- non-ACTIVE means we
            // will just return an empty list

            // Guid cannot be null here, as validation above ensures it is non-null
            AtlasGuid atlasGuid = AtlasGuid.fromGuid(entityGUID);
            String prefix = atlasGuid.getGeneratedPrefix();

            // 1. retrieve entity from Apache Atlas by GUID (including its relationships)
            AtlasEntity.AtlasEntityWithExtInfo asset = null;
            try {
                asset = atlasRepositoryConnector.getEntityByGUID(atlasGuid.getAtlasGuid(), false, false);
            } catch (AtlasServiceException e) {
                raiseEntityNotKnownException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, entityGUID, methodName, repositoryName);
            }

            // Ensure the entity actually exists (if not, throw error to that effect)
            if (asset == null) {
                raiseEntityNotKnownException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, null, entityGUID, methodName, repositoryName);
            } else {

                EntityMappingAtlas2OMRS entityMap = new EntityMappingAtlas2OMRS(
                        atlasRepositoryConnector,
                        typeDefStore,
                        attributeTypeDefStore,
                        asset,
                        prefix,
                        userId
                );

                // 2. Apply the mapping to the object, and retrieve the resulting relationships
                alRelationships = entityMap.getRelationships(
                        relationshipTypeGUID,
                        fromRelationshipElement,
                        sequencingProperty,
                        sequencingOrder,
                        pageSize
                );

            }

        }

        return alRelationships;

    }

    /**
     * Return a list of entities that match the supplied properties according to the match criteria.  The results
     * can be returned over many pages.
     *
     * @param userId unique identifier for requesting user.
     * @param entityTypeGUID String unique identifier for the entity type of interest (null means any entity type).
     * @param matchProperties Optional list of entity properties to match (where any String property's value should
     *                        be defined as a Java regular expression, even if it should be an exact match).
     * @param matchCriteria Enum defining how the match properties should be matched to the entities in the repository.
     * @param fromEntityElement the starting element number of the entities to return.
     *                                This is used when retrieving elements
     *                                beyond the first page of results. Zero means start from the first element.
     * @param limitResultsByStatus By default, entities in all statuses are returned.  However, it is possible
     *                             to specify a list of statuses (eg ACTIVE) to restrict the results to.  Null means all
     *                             status values.
     * @param limitResultsByClassification List of classifications that must be present on all returned entities.
     * @param asOfTime Requests a historical query of the entity (not implemented for Atlas).
     * @param sequencingProperty String name of the entity property that is to be used to sequence the results.
     *                           Null means do not sequence on a property name (see SequencingOrder).
     * @param sequencingOrder Enum defining how the results should be ordered.
     * @param pageSize the maximum number of result entities that can be returned on this request.  Zero means
     *                 unrestricted return results size.
     * @return a list of entities matching the supplied criteria; null means no matching entities in the metadata
     * collection.
     * @throws InvalidParameterException a parameter is invalid or null.
     * @throws TypeErrorException the type guid passed on the request is not known by the
     *                              metadata collection.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                    the metadata collection is stored.
     * @throws PropertyErrorException the properties specified are not valid for any of the requested types of
     *                                  entity.
     * @throws PagingErrorException the paging/sequencing parameters are set up incorrectly.
     * @throws FunctionNotSupportedException the repository does not support one of the provided parameters.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.
     * @see OMRSRepositoryHelper#getExactMatchRegex(String)
     */
    @Override
    public  List<EntityDetail> findEntitiesByProperty(String                    userId,
                                                      String                    entityTypeGUID,
                                                      InstanceProperties        matchProperties,
                                                      MatchCriteria             matchCriteria,
                                                      int                       fromEntityElement,
                                                      List<InstanceStatus>      limitResultsByStatus,
                                                      List<String>              limitResultsByClassification,
                                                      Date                      asOfTime,
                                                      String                    sequencingProperty,
                                                      SequencingOrder           sequencingOrder,
                                                      int                       pageSize) throws InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PropertyErrorException,
            PagingErrorException,
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "findEntitiesByProperty";
        findEntitiesByPropertyParameterValidation(
                userId,
                entityTypeGUID,
                matchProperties,
                matchCriteria,
                fromEntityElement,
                limitResultsByStatus,
                limitResultsByClassification,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize
        );

        List<AtlasEntityHeader> results = null;

        // Immediately throw unimplemented exception if trying to retrieve historical view
        if (asOfTime != null) {
            raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode.NO_HISTORY, methodName, repositoryName);
        } else if (sequencingOrder != null || (limitResultsByClassification != null && limitResultsByClassification.size() > 1)) {

            results = buildAndRunDSLSearch(
                    methodName,
                    entityTypeGUID,
                    limitResultsByClassification,
                    matchProperties,
                    matchCriteria,
                    fromEntityElement,
                    limitResultsByStatus,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize,
                    userId
            );

        } else {

            // Only take the first classification for a basic search (if there were multiple, should be handled above
            // by DSL query)
            results = buildAndRunBasicSearch(
                    methodName,
                    entityTypeGUID,
                    (limitResultsByClassification == null ? null : limitResultsByClassification.get(0)),
                    matchProperties,
                    matchCriteria,
                    null,
                    fromEntityElement,
                    limitResultsByStatus,
                    pageSize,
                    userId
            );

        }

        List<EntityDetail> entityDetails = null;
        if (results != null) {
            entityDetails = sortAndLimitFinalResults(
                    results,
                    entityTypeGUID,
                    fromEntityElement,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize,
                    userId
            );
        }
        return (entityDetails == null || entityDetails.isEmpty()) ? null : entityDetails;

    }

    /**
     * Return a list of entities that have the requested type of classifications attached.
     *
     * @param userId unique identifier for requesting user.
     * @param entityTypeGUID unique identifier for the type of entity requested.  Null means any type of entity
     *                       (but could be slow so not recommended.
     * @param classificationName name of the classification, note a null is not valid.
     * @param matchClassificationProperties list of classification properties used to narrow the search (where any String
     *                                      property's value should be defined as a Java regular expression, even if it
     *                                      should be an exact match).
     * @param matchClassificationCriteria Enum defining how the match properties should be matched to the classifications in the repository.
     * @param fromEntityElement the starting element number of the entities to return.
     *                                This is used when retrieving elements
     *                                beyond the first page of results. Zero means start from the first element.
     * @param limitResultsByStatus By default, entities in all statuses are returned.  However, it is possible
     *                             to specify a list of statuses (eg ACTIVE) to restrict the results to.  Null means all
     *                             status values.
     * @param asOfTime Requests a historical query of the entity.  Null means return the present values.
     * @param sequencingProperty String name of the classification property that is to be used to sequence the results.
     *                           Null means do not sequence on a property name (see SequencingOrder).
     * @param sequencingOrder Enum defining how the results should be ordered.
     * @param pageSize the maximum number of result entities that can be returned on this request.  Zero means
     *                 unrestricted return results size.
     * @return a list of entities matching the supplied criteria; null means no matching entities in the metadata
     * collection.
     * @throws InvalidParameterException a parameter is invalid or null.
     * @throws TypeErrorException the type guid passed on the request is not known by the
     *                              metadata collection.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                    the metadata collection is stored.
     * @throws ClassificationErrorException the classification request is not known to the metadata collection.
     * @throws PropertyErrorException the properties specified are not valid for the requested type of
     *                                  classification.
     * @throws PagingErrorException the paging/sequencing parameters are set up incorrectly.
     * @throws FunctionNotSupportedException the repository does not support one of the provided parameters.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.
     * @see OMRSRepositoryHelper#getExactMatchRegex(String)
     */
    @Override
    public  List<EntityDetail> findEntitiesByClassification(String                    userId,
                                                            String                    entityTypeGUID,
                                                            String                    classificationName,
                                                            InstanceProperties        matchClassificationProperties,
                                                            MatchCriteria             matchClassificationCriteria,
                                                            int                       fromEntityElement,
                                                            List<InstanceStatus>      limitResultsByStatus,
                                                            Date                      asOfTime,
                                                            String                    sequencingProperty,
                                                            SequencingOrder           sequencingOrder,
                                                            int                       pageSize) throws InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            ClassificationErrorException,
            PropertyErrorException,
            PagingErrorException,
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        final String methodName = "findEntitiesByClassification";
        findEntitiesByClassificationParameterValidation(
                userId,
                entityTypeGUID,
                classificationName,
                matchClassificationProperties,
                matchClassificationCriteria,
                fromEntityElement,
                limitResultsByStatus,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize
        );

        List<AtlasEntityHeader> results = null;

        // Immediately throw unimplemented exception if trying to retrieve historical view
        if (asOfTime != null) {
            raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode.NO_HISTORY, methodName, repositoryName);
        } else if (sequencingOrder != null) {

            List<String> limitResultsByClassification = new ArrayList<>();
            limitResultsByClassification.add(classificationName);

            // TODO: need a further check whether we are being asked to sequence by property: if so,
            //  it is the _classification_ property not the _entity_ property, so we need a post-search-sorting

            // Run the base search first (and if we need to match on classification properties, increase pageSize
            // so there is buffer to cull later)
            results = buildAndRunDSLSearch(
                    methodName,
                    entityTypeGUID,
                    limitResultsByClassification,
                    null,
                    matchClassificationCriteria,
                    fromEntityElement,
                    limitResultsByStatus,
                    sequencingProperty,
                    sequencingOrder,
                    matchClassificationProperties == null ? pageSize : pageSize * 2,
                    userId
            );
            // TODO: still a risk that there are many classified entities and we overflow beyond this increased pageSize

        } else {

            // Run the base search first (and if we need to match on classification properties, increase pageSize
            // so there is buffer to cull later)
            results = buildAndRunBasicSearch(
                    methodName,
                    entityTypeGUID,
                    classificationName,
                    null,
                    matchClassificationCriteria,
                    null,
                    fromEntityElement,
                    limitResultsByStatus,
                    matchClassificationProperties == null ? pageSize : pageSize * 2,
                    userId
            );
            // TODO: still a risk that there are many classified entities and we overflow beyond this increased pageSize

        }

        // Then limit any results based on the classification properties (if any were provided)
        List<AtlasEntityHeader> atlasEntities = new ArrayList<>();
        if (results != null && matchClassificationProperties != null) {

            Map<String, InstancePropertyValue> propertiesToMatch = matchClassificationProperties.getInstanceProperties();

            if (propertiesToMatch != null) {
                // For each entity we've preliminarily identified...
                for (AtlasEntityHeader candidateEntity : results) {
                    List<AtlasClassification> classificationsForEntity = candidateEntity.getClassifications();
                    // ... iterate through each of its classifications to narrow in on only the one of interest
                    for (AtlasClassification candidateClassification : classificationsForEntity) {
                        if (candidateClassification.getTypeName().equals(classificationName)) {
                            // ... then iterate through the properties of that classification we're trying to match
                            boolean bMatchesAny = false;
                            boolean bMatchesAll = true;
                            for (Map.Entry<String, InstancePropertyValue> propertyToMatch : propertiesToMatch.entrySet()) {
                                String propertyName = propertyToMatch.getKey();
                                InstancePropertyValue omrsPropertyValueToMatch = propertyToMatch.getValue();
                                // Remember that classifications (and their properties) are one-to-one with Egeria (no mapping needed)
                                Object atlasClassificationValue = candidateClassification.getAttribute(propertyName);
                                boolean bMatchesThisOne = AttributeMapping.valuesMatch(omrsPropertyValueToMatch, atlasClassificationValue);
                                bMatchesAll = bMatchesAll && bMatchesThisOne;
                                bMatchesAny = bMatchesAny || bMatchesThisOne;
                            }
                            if (matchClassificationCriteria != null) {
                                switch (matchClassificationCriteria) {
                                    case NONE:
                                        if (!bMatchesAny) {
                                            atlasEntities.add(candidateEntity);
                                        }
                                        break;
                                    case ALL:
                                        if (bMatchesAll) {
                                            atlasEntities.add(candidateEntity);
                                        }
                                        break;
                                    case ANY:
                                        if (bMatchesAny) {
                                            atlasEntities.add(candidateEntity);
                                        }
                                        break;
                                }
                            } else if (bMatchesAll) {
                                atlasEntities.add(candidateEntity);
                            } else {
                                log.debug("Unable to match properties '{}' for entity, dropping from results: {}", matchClassificationProperties, candidateEntity);
                            }
                        }
                    }
                }
            } else {
                // If there were no properties in the match set (it was empty), just grab the results directly
                atlasEntities = results;
            }

            // ... trim the results list to size, before we retrieve full EntityDetails for each
            int endOfPageMarker = Math.min(fromEntityElement + pageSize, atlasEntities.size());
            if (fromEntityElement != 0 || endOfPageMarker < atlasEntities.size()) {
                atlasEntities = atlasEntities.subList(fromEntityElement, endOfPageMarker);
            }

        } else if (results != null) {
            // If no classification properties to limit, just grab the results directly
            atlasEntities = results;
        }

        List<EntityDetail> entityDetails = sortAndLimitFinalResults(
                atlasEntities,
                entityTypeGUID,
                fromEntityElement,
                sequencingProperty,
                sequencingOrder,
                pageSize,
                userId
        );
        return (entityDetails == null || entityDetails.isEmpty()) ? null : entityDetails;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntityDetail> findEntitiesByPropertyValue(String userId,
                                                          String entityTypeGUID,
                                                          String searchCriteria,
                                                          int fromEntityElement,
                                                          List<InstanceStatus> limitResultsByStatus,
                                                          List<String> limitResultsByClassification,
                                                          Date asOfTime,
                                                          String sequencingProperty,
                                                          SequencingOrder sequencingOrder,
                                                          int pageSize) throws
            InvalidParameterException,
            TypeErrorException,
            RepositoryErrorException,
            PropertyErrorException,
            PagingErrorException,
            FunctionNotSupportedException,
            UserNotAuthorizedException {

        final String  methodName = "findEntitiesByPropertyValue";
        findEntitiesByPropertyValueParameterValidation(
                userId,
                entityTypeGUID,
                searchCriteria,
                fromEntityElement,
                limitResultsByStatus,
                limitResultsByClassification,
                asOfTime,
                sequencingProperty,
                sequencingOrder,
                pageSize
        );

        List<AtlasEntityHeader> results = new ArrayList<>();

        // Immediately throw unimplemented exception if trying to retrieve historical view
        if (asOfTime != null) {
            raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode.NO_HISTORY, methodName, repositoryName);
        }

        InstanceProperties matchProperties = null;

        // Search criteria is not allowed to be empty for this method, so cannot be null or empty string.
        if (repositoryHelper.isContainsRegex(searchCriteria) && sequencingOrder == null && (limitResultsByClassification == null || limitResultsByClassification.size() == 1)) {
            // If the search criteria is a contains regex, no sorting is required, and limiting by classification is at most
            // one, we can do a full text-based query in Atlas
            results = buildAndRunBasicSearch(
                    methodName,
                    entityTypeGUID,
                    (limitResultsByClassification == null ? null : limitResultsByClassification.get(0)),
                    null,
                    null,
                    searchCriteria,
                    fromEntityElement,
                    limitResultsByStatus,
                    pageSize,
                    userId
            );
        } else {

            // Otherwise we need to do an OR-based search across all string properties in Atlas, using whatever the
            // regex of searchCriteria contains for each property
            matchProperties = new InstanceProperties();

            // Add all textual properties of the provided entity as matchProperties,
            //  for an OR-based search of their values
            Map<String, Map<String, Set<String>>> mappingsToSearch = getMappingsToSearch(entityTypeGUID, userId);
            for (Map.Entry<String, Map<String, Set<String>>> entryToSearch : mappingsToSearch.entrySet()) {

                String omrsTypeName = entryToSearch.getKey();
                String omrsTypeGUID = typeDefStore.getTypeDefByName(omrsTypeName).getGUID();

                Map<String, TypeDefAttribute> typeDefAttributeMap = typeDefStore.getAllTypeDefAttributesForName(omrsTypeName);

                if (typeDefAttributeMap != null) {
                    // This will look at all OMRS attributes, but buildAndRunDSLSearch (later) should limit to only those mapped to Atlas
                    for (Map.Entry<String, TypeDefAttribute> attributeEntry : typeDefAttributeMap.entrySet()) {
                        String attributeName = attributeEntry.getKey();
                        TypeDefAttribute typeDefAttribute = attributeEntry.getValue();
                        // Only need to retain string-based attributes for the full text search
                        AttributeTypeDef attributeTypeDef = typeDefAttribute.getAttributeType();
                        if (attributeTypeDef.getCategory().equals(AttributeTypeDefCategory.PRIMITIVE)) {
                            PrimitiveDefCategory primitiveDefCategory = ((PrimitiveDef) attributeTypeDef).getPrimitiveDefCategory();
                            if (primitiveDefCategory.equals(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING)
                                    || primitiveDefCategory.equals(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_BYTE)
                                    || primitiveDefCategory.equals(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_CHAR)) {
                                matchProperties = repositoryHelper.addStringPropertyToInstance(
                                        repositoryName,
                                        matchProperties,
                                        attributeName,
                                        searchCriteria,
                                        methodName
                                );
                            } else {
                                log.debug("Skipping inclusion of non-string attribute: {}", attributeName);
                            }
                        } else {
                            log.debug("Skipping inclusion of non-string attribute: {}", attributeName);
                        }
                    }
                }

                List<AtlasEntityHeader> innerResults;
                if (sequencingOrder != null || (limitResultsByClassification != null && limitResultsByClassification.size() > 1)) {
                    // If we need to do any sequencing or limiting by multiple classifications, then we must run a DSL search
                    innerResults = buildAndRunDSLSearch(
                            methodName,
                            omrsTypeGUID,
                            limitResultsByClassification,
                            matchProperties,
                            MatchCriteria.ANY,
                            fromEntityElement,
                            limitResultsByStatus,
                            sequencingProperty,
                            sequencingOrder,
                            pageSize,
                            userId
                    );
                } else {
                    // Otherwise we can still do a basic search -- only take the first classification for a basic search
                    // (if there were multiple, should be handled above by DSL query)
                    innerResults = buildAndRunBasicSearch(
                            methodName,
                            omrsTypeGUID,
                            (limitResultsByClassification == null ? null : limitResultsByClassification.get(0)),
                            matchProperties,
                            MatchCriteria.ANY,
                            null,
                            fromEntityElement,
                            limitResultsByStatus,
                            pageSize,
                            userId
                    );
                }

                if (innerResults != null) {
                    results.addAll(innerResults);
                }

            }

        }

        List<EntityDetail> entityDetails = null;
        if (results != null) {
            entityDetails = sortAndLimitFinalResults(
                    results,
                    entityTypeGUID,
                    fromEntityElement,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize,
                    userId
            );
        }
        return (entityDetails == null || entityDetails.isEmpty()) ? null : entityDetails;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship isRelationshipKnown(String userId,
                                            String guid) throws
            InvalidParameterException,
            RepositoryErrorException {

        final String methodName = "isRelationshipKnown";
        super.getInstanceParameterValidation(userId, guid, methodName);

        Relationship relationship = null;
        try {
            relationship = getRelationship(userId, guid);
        } catch (RelationshipNotKnownException e) {
            log.info("Relationship {} not known to the repository.", guid, e);
        }
        return relationship;

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Relationship getRelationship(String userId,
                                        String guid) throws InvalidParameterException,
            RepositoryErrorException,
            RelationshipNotKnownException {

        final String methodName = "getRelationship";
        super.getInstanceParameterValidation(userId, guid, methodName);

        // Guid cannot be null here, as validation above ensures it is non-null
        AtlasGuid atlasGuid = AtlasGuid.fromGuid(guid);
        if (atlasGuid.isGeneratedInstanceGuid()) {
            // If this is a self-referencing relationship, we need to construct it by retrieving the entity (not
            // a relationship) from Atlas
            Relationship relationship = null;
            try {
                AtlasEntity.AtlasEntityWithExtInfo entity = atlasRepositoryConnector.getEntityByGUID(atlasGuid.getAtlasGuid(), true, true);
                if (entity != null) {
                    relationship = RelationshipMapping.getSelfReferencingRelationship(
                            atlasRepositoryConnector,
                            typeDefStore,
                            atlasGuid,
                            entity.getEntity());
                } else {
                    raiseRelationshipNotKnownException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, null, guid, methodName, repositoryName);
                }
            } catch (AtlasServiceException e) {
                raiseRelationshipNotKnownException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, e, guid, methodName, repositoryName);
            }
            return relationship;
        } else {
            // Otherwise we should be able to directly retrieve a relationship from Atlas
            AtlasRelationship.AtlasRelationshipWithExtInfo relationship = null;
            try {
                relationship = this.atlasRepositoryConnector.getRelationshipByGUID(atlasGuid.getAtlasGuid());
            } catch (AtlasServiceException e) {
                raiseRelationshipNotKnownException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, e, guid, methodName, repositoryName);
            }
            if (relationship == null) {
                raiseRelationshipNotKnownException(ApacheAtlasOMRSErrorCode.RELATIONSHIP_NOT_KNOWN, methodName, null, guid, methodName, repositoryName);
            }
            RelationshipMapping mapping = new RelationshipMapping(atlasRepositoryConnector, typeDefStore, attributeTypeDefStore, atlasGuid, relationship, userId);
            return mapping.getRelationship();
        }

    }

    /**
     * Save the entity as a reference copy.  The id of the home metadata collection is already set up in the
     * entity.
     *
     * @param userId unique identifier for requesting server.
     * @param entity details of the entity to save.
     * @throws InvalidParameterException the entity is null.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                    the metadata collection is stored.
     * @throws TypeErrorException the requested type is not known, or not supported in the metadata repository
     *                            hosting the metadata collection.
     * @throws PropertyErrorException one or more of the requested properties are not defined, or have different
     *                                  characteristics in the TypeDef for this entity's type.
     * @throws HomeEntityException the entity belongs to the local repository so creating a reference
     *                               copy would be invalid.
     * @throws EntityConflictException the new entity conflicts with an existing entity.
     * @throws InvalidEntityException the new entity has invalid contents.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.

    @Override
    public void saveEntityReferenceCopy(String userId,
                                        EntityDetail   entity) throws InvalidParameterException,
            RepositoryErrorException,
            TypeErrorException,
            PropertyErrorException,
            HomeEntityException,
            EntityConflictException,
            InvalidEntityException,
            UserNotAuthorizedException {

        final String  methodName = "saveEntityReferenceCopy";

        // Validate parameters
        this.validateRepositoryConnector(methodName);
        parentConnector.validateRepositoryIsActive(methodName);

        if (entity == null) {
            ApacheAtlasOMRSErrorCode errorCode = ApacheAtlasOMRSErrorCode.INSTANCE_NOT_PROVIDED;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(
                    methodName,
                    repositoryName
            );
            throw new InvalidParameterException(errorCode.getHTTPErrorCode(),
                    this.getClass().getName(),
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction());
        }

        InstanceType type = entity.getType();
        String typeName = type.getTypeDefName();
        String atlasTypeName = typeDefStore.getMappedAtlasTypeDefName(typeName);

        if (atlasTypeName == null) {
            ApacheAtlasOMRSErrorCode errorCode = ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_KNOWN_FOR_INSTANCE;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(
                    typeName,
                    entity.getGUID(),
                    methodName,
                    repositoryName
            );
            throw new TypeErrorException(errorCode.getHTTPErrorCode(),
                    this.getClass().getName(),
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction());
        }

        String receivedCollectionId = entity.getMetadataCollectionId();
        if (metadataCollectionId.equals(receivedCollectionId)) {
            ApacheAtlasOMRSErrorCode errorCode = ApacheAtlasOMRSErrorCode.INSTANCE_ALREADY_HOME;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(
                    methodName,
                    repositoryName
            );
            throw new HomeEntityException(errorCode.getHTTPErrorCode(),
                    this.getClass().getName(),
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction());
        }

        EntityMappingOMRS2Atlas mapping = new EntityMappingOMRS2Atlas(atlasRepositoryConnector, typeDefStore, attributeTypeDefStore, entity, userId);

        // Attempt to save the reference copy, and throw an exception if unsuccessful
        EntityMutations.EntityOperation result = mapping.saveReferenceCopy();
        if (result == null) {
            ApacheAtlasOMRSErrorCode errorCode = ApacheAtlasOMRSErrorCode.UNABLE_TO_SAVE;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(
                    methodName,
                    repositoryName
            );
            throw new RepositoryErrorException(errorCode.getHTTPErrorCode(),
                    this.getClass().getName(),
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction());
        }

    }
    */

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshEntityReferenceCopy(String userId,
                                           String entityGUID,
                                           String typeDefGUID,
                                           String typeDefName,
                                           String homeMetadataCollectionId) throws
            InvalidParameterException,
            RepositoryErrorException,
            HomeEntityException,
            UserNotAuthorizedException {

        final String methodName = "refreshEntityReferenceCopy";
        final String entityParameterName = "entityGUID";
        final String homeParameterName = "homeMetadataCollectionId";

        /*
         * Validate parameters
         */
        super.manageReferenceInstanceParameterValidation(userId,
                entityGUID,
                typeDefGUID,
                typeDefName,
                entityParameterName,
                homeMetadataCollectionId,
                homeParameterName,
                methodName);

        // Only bother doing any work if there is an event mapper configured into which to actually push
        // the refresh event
        if (eventMapper != null) {
            /*
             * Validate that the entity GUID is ok
             */
            EntityDetail entity = this.isEntityKnown(userId, entityGUID);
            if (entity != null) {
                if (metadataCollectionId.equals(entity.getMetadataCollectionId())) {
                    throw new HomeEntityException(ApacheAtlasOMRSErrorCode.HOME_REFRESH.getMessageDefinition(methodName, entityGUID, metadataCollectionId, repositoryName),
                            this.getClass().getName(),
                            methodName);
                }
            }

            /*
             * Send refresh message
             */
            eventMapper.sendRefreshEntityRequest(
                    typeDefGUID,
                    typeDefName,
                    entityGUID,
                    homeMetadataCollectionId
            );
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshRelationshipReferenceCopy(String userId,
                                                 String relationshipGUID,
                                                 String typeDefGUID,
                                                 String typeDefName,
                                                 String homeMetadataCollectionId) throws
            InvalidParameterException,
            RepositoryErrorException,
            HomeRelationshipException,
            UserNotAuthorizedException {

        final String methodName = "refreshRelationshipReferenceCopy";
        final String relationshipParameterName = "relationshipGUID";
        final String homeParameterName = "homeMetadataCollectionId";

        /*
         * Validate parameters
         */
        super.manageReferenceInstanceParameterValidation(userId,
                relationshipGUID,
                typeDefGUID,
                typeDefName,
                relationshipParameterName,
                homeMetadataCollectionId,
                homeParameterName,
                methodName);

        // Only bother doing any work if there is an event mapper configured into which to actually push
        // the refresh event
        if (eventMapper != null) {
            Relationship relationship = this.isRelationshipKnown(userId, relationshipGUID);
            if (relationship != null) {
                if (metadataCollectionId.equals(relationship.getMetadataCollectionId())) {
                    throw new HomeRelationshipException(ApacheAtlasOMRSErrorCode.HOME_REFRESH.getMessageDefinition(methodName, relationshipGUID, metadataCollectionId, repositoryName),
                            this.getClass().getName(),
                            methodName);
                }
            }

            /*
             * Process refresh request
             */
            eventMapper.sendRefreshRelationshipRequest(
                    typeDefGUID,
                    typeDefName,
                    relationshipGUID,
                    homeMetadataCollectionId
            );
        }

    }

    /**
     * Configure the event mapper that should be used to send any outbound events.
     *
     * @param eventMapper the event mapper to use
     */
    public void setEventMapper(ApacheAtlasOMRSRepositoryEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * Retrieve the type definitions that are mapped for this repository.
     *
     * @return TypeDefStore
     */
    public TypeDefStore getTypeDefStore() { return this.typeDefStore; }

    /**
     * Retrieve the attribute definitions that are mapped for this repository.
     *
     * @return AttributeTypeDefStore
     */
    public AttributeTypeDefStore getAttributeTypeDefStore() { return this.attributeTypeDefStore; }

    /**
     * Retrieve the set of states that the repository supports.
     *
     * @return {@code Set<InstanceStatus>}
     */
    public Set<InstanceStatus> getAvailableStates() { return this.availableStates; }

    /**
     * Retrieve the listing of implemented mappings that should be used for an entity search, including navigating
     * subtypes when a supertype is the entity type provided.  The result will be a map of OMRS type name to a map
     * keyed by prefix (which could be null) to the mapped atlas type names.
     *
     * @param entityTypeGUID the GUID of the OMRS entity type for which to search
     * @param userId the userId through which to search
     * @return {@code Map<String, Map<String, Set<String>>>}
     * @throws RepositoryErrorException on any unexpected error
     */
    private Map<String, Map<String, Set<String>>> getMappingsToSearch(String entityTypeGUID, String userId) throws
            RepositoryErrorException {

        Map<String, Map<String, Set<String>>> results = new HashMap<>();
        Map<String, Set<String>> atlasTypeNamesByPrefix = new HashMap<>();
        String requestedTypeName = null;
        if (entityTypeGUID != null) {
            TypeDef typeDef = typeDefStore.getTypeDefByGUID(entityTypeGUID, false);
            if (typeDef != null) {
                String omrsTypeName = typeDef.getName();
                if (typeDefStore.isUnimplementedSupertype(omrsTypeName)) {
                    requestedTypeName = omrsTypeName;
                } else {
                    atlasTypeNamesByPrefix = typeDefStore.getAllMappedAtlasTypeDefNames(omrsTypeName);
                    results.put(omrsTypeName, atlasTypeNamesByPrefix);
                }
            } else {
                TypeDef unimplemented = typeDefStore.getUnimplementedTypeDefByGUID(entityTypeGUID);
                requestedTypeName = unimplemented.getName();
            }
        } else {
            Set<String> set = new HashSet<>();
            set.add("Referenceable");
            atlasTypeNamesByPrefix.put(null, set);
            results.put("Referenceable", atlasTypeNamesByPrefix);
        }

        // If the requested type is one that is not directly mapped, we need to scan whether we have implemented
        // any of its sub-types to include in the results
        if (requestedTypeName != null) {
            try {
                List<TypeDef> allEntityTypes = findTypeDefsByCategory(userId, TypeDefCategory.ENTITY_DEF);
                for (TypeDef typeDef : allEntityTypes) {
                    String typeDefName = typeDef.getName();
                    if (!typeDefName.equals("Referenceable")
                            && !typeDefStore.isUnimplementedSupertype(typeDefName)
                            && repositoryHelper.isTypeOf(metadataCollectionId, typeDefName, requestedTypeName)) {
                        Map<String, Set<String>> mappings = typeDefStore.getAllMappedAtlasTypeDefNames(typeDefName);
                        if (!mappings.isEmpty()) {
                            results.put(typeDefName, mappings);
                        }
                    }
                }
            } catch (InvalidParameterException e) {
                log.error("An invalid parameter was provided to findTypeDefsByCategory, cannot determine types to include in search.", e);
            }
        }

        return results;

    }

    /**
     * Build an Atlas domain-specific language (DSL) query based on the provided parameters, and return its results.
     *
     * @param methodName the name of the calling method
     * @param entityTypeGUID unique identifier for the type of entity requested.  Null means any type of entity
     *                       (but could be slow so not recommended.
     * @param limitResultsByClassification list of classifications by which to limit the results.
     * @param matchProperties Optional list of entity properties to match (contains wildcards).
     * @param matchCriteria Enum defining how the match properties should be matched to the classifications in the repository.
     * @param fromEntityElement the starting element number of the entities to return.
     *                                This is used when retrieving elements
     *                                beyond the first page of results. Zero means start from the first element.
     * @param limitResultsByStatus By default, entities in all statuses are returned.  However, it is possible
     *                             to specify a list of statuses (eg ACTIVE) to restrict the results to.  Null means all
     *                             status values.
     * @param sequencingProperty String name of the entity property that is to be used to sequence the results.
     *                           Null means do not sequence on a property name (see SequencingOrder).
     * @param sequencingOrder Enum defining how the results should be ordered.
     * @param pageSize the maximum number of result entities that can be returned on this request.  Zero means
     *                 unrestricted return results size.
     * @param userId the user through which to run the search
     * @return {@code List<AtlasEntityHeader>}
     * @throws FunctionNotSupportedException when trying to search using a status that is not supported in Atlas
     * @throws RepositoryErrorException when there is some error running the search against Atlas
     */
    private List<AtlasEntityHeader> buildAndRunDSLSearch(String methodName,
                                                         String entityTypeGUID,
                                                         List<String> limitResultsByClassification,
                                                         InstanceProperties matchProperties,
                                                         MatchCriteria matchCriteria,
                                                         int fromEntityElement,
                                                         List<InstanceStatus> limitResultsByStatus,
                                                         String sequencingProperty,
                                                         SequencingOrder sequencingOrder,
                                                         int pageSize,
                                                         String userId) throws
            FunctionNotSupportedException,
            RepositoryErrorException {

        // If we need to order the results, it will probably be more efficient to use Atlas's DSL query language
        // to do the search
        boolean skipSearch = false;

        // Run multiple searches, if there are multiple types mapped to the OMRS type...
        Map<String, Map<String, Set<String>>> mappingsToSearch = getMappingsToSearch(entityTypeGUID, userId);
        List<AtlasSearchResult> totalResults = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> entryToSearch : mappingsToSearch.entrySet()) {
            String omrsTypeName = entryToSearch.getKey();
            Map<String, Set<String>> atlasTypeNamesByPrefix = entryToSearch.getValue();

            for (Map.Entry<String, Set<String>> entry : atlasTypeNamesByPrefix.entrySet()) {

                String prefix = entry.getKey();
                Set<String> atlasTypeNames = entry.getValue();

                for (String atlasTypeName : atlasTypeNames) {
                    Map<String, String> omrsPropertyMap = typeDefStore.getPropertyMappingsForOMRSTypeDef(omrsTypeName, prefix);

                    StringBuilder sb = new StringBuilder();
                    sb.append("from ");
                    sb.append(atlasTypeName);
                    boolean bWhereClauseAdded = false;

                    // Add the multiple classification criteria, if requested
                    // (recall that OMRS classification name should be identical to Atlas classification name -- no translation needed)
                    if (limitResultsByClassification != null) {
                        List<String> classifications = new ArrayList<>();
                        for (String classificationName : limitResultsByClassification) {
                            classifications.add(atlasTypeName + " isa " + classificationName);
                        }
                        if (!classifications.isEmpty()) {
                            sb.append(" where ");
                            sb.append(String.join(" and ", classifications));
                            bWhereClauseAdded = true;
                        }
                    }

                    // Add match properties, if requested
                    if (matchProperties != null) {
                        List<String> propertyCriteria = new ArrayList<>();
                        Map<String, InstancePropertyValue> properties = matchProperties.getInstanceProperties();
                        // By default, include only Referenceable's properties (as these will be the only properties that exist
                        // across ALL entity types)
                        Map<String, TypeDefAttribute> omrsAttrTypeDefs = typeDefStore.getAllTypeDefAttributesForName(omrsTypeName);
                        if (properties != null) {
                            for (Map.Entry<String, InstancePropertyValue> property : properties.entrySet()) {
                                String omrsPropertyName = property.getKey();
                                InstancePropertyValue value = property.getValue();
                                boolean added = addSearchConditionFromValue(
                                        propertyCriteria,
                                        omrsPropertyName,
                                        value,
                                        omrsPropertyMap,
                                        omrsAttrTypeDefs,
                                        (matchCriteria != null) && matchCriteria.equals(MatchCriteria.NONE),
                                        true
                                );
                                if (!added) {
                                    if (matchCriteria == null || matchCriteria.equals(MatchCriteria.ALL)) {
                                        // If we are asked to find everything, but one of the properties cannot be searched,
                                        // then we should skip the search
                                        skipSearch = true;
                                        log.info("Skipping search ({}) -- ALL criteria should match, but this one is unmapped: {}", entityTypeGUID, omrsPropertyName);
                                    }
                                }
                            }
                        }
                        if (!propertyCriteria.isEmpty()) {
                            String propertyMatchDelim = " and ";
                            if (matchCriteria != null && matchCriteria.equals(MatchCriteria.ANY)) {
                                propertyMatchDelim = " or ";
                            }
                            if (!bWhereClauseAdded) {
                                sb.append(" where");
                            }
                            sb.append(" ");
                            sb.append(String.join(propertyMatchDelim, propertyCriteria));
                        }
                    }

                    // Add status limiters, if requested
                    boolean unsupportedStatusRequested = false;
                    if (limitResultsByStatus != null) {
                        List<String> states = new ArrayList<>();
                        Set<InstanceStatus> limitSet = new HashSet<>(limitResultsByStatus);
                        for (InstanceStatus requestedStatus : limitSet) {
                            switch (requestedStatus) {
                                case ACTIVE:
                                    states.add("__state = 'ACTIVE'");
                                    break;
                                case DELETED:
                                    states.add("__state = 'DELETED'");
                                    break;
                                default:
                                    unsupportedStatusRequested = true;
                                    break;
                            }
                        }
                        if (!states.isEmpty()) {
                            if (!bWhereClauseAdded) {
                                sb.append(" where");
                            }
                            sb.append(" ");
                            sb.append(String.join(" or ", states));
                        } else if (unsupportedStatusRequested) {
                            // We are searching only for a state that Atlas does not support, so we should ensure no
                            // results are returned (in fact, skip searching entirely).
                            skipSearch = true;
                            log.info("Skipping search ({}) -- one or more unsupported statuses requested: {}", entityTypeGUID, limitSet);
                        }
                    }

                    if (!skipSearch) {
                        // Add sorting criteria, if requested
                        if (sequencingOrder != null) {
                            switch (sequencingOrder) {
                                case GUID:
                                    sb.append(" orderby __guid asc");
                                    break;
                                case LAST_UPDATE_OLDEST:
                                    sb.append(" orderby __modificationTimestamp asc");
                                    break;
                                case LAST_UPDATE_RECENT:
                                    sb.append(" orderby __modificationTimestamp desc");
                                    break;
                                case CREATION_DATE_OLDEST:
                                    sb.append(" orderby __timestamp asc");
                                    break;
                                case CREATION_DATE_RECENT:
                                    sb.append(" orderby __timestamp desc");
                                    break;
                                case PROPERTY_ASCENDING:
                                    if (sequencingProperty != null) {
                                        String atlasPropertyName = omrsPropertyMap.get(sequencingProperty);
                                        if (atlasPropertyName != null) {
                                            sb.append(" orderby ");
                                            sb.append(atlasPropertyName);
                                            sb.append(" asc");
                                        } else {
                                            log.warn("Unable to find mapped Atlas property for sorting for: {}", sequencingProperty);
                                            sb.append(" orderby __guid asc");
                                        }
                                    } else {
                                        log.warn("No property for sorting provided, defaulting to GUID.");
                                        sb.append(" orderby __guid asc");
                                    }
                                    break;
                                case PROPERTY_DESCENDING:
                                    if (sequencingProperty != null) {
                                        String atlasPropertyName = omrsPropertyMap.get(sequencingProperty);
                                        if (atlasPropertyName != null) {
                                            sb.append(" orderby ");
                                            sb.append(atlasPropertyName);
                                            sb.append(" desc");
                                        } else {
                                            log.warn("Unable to find mapped Atlas property for sorting for: {}", sequencingProperty);
                                            sb.append(" orderby __guid asc");
                                        }
                                    } else {
                                        log.warn("No property for sorting provided, defaulting to GUID.");
                                        sb.append(" orderby __guid desc");
                                    }
                                    break;
                                default:
                                    // Do nothing -- no sorting
                                    break;
                            }
                        }

                        // Add paging criteria, if requested
                        if (pageSize > 0) {
                            sb.append(" limit ");
                            sb.append(pageSize);
                        }
                        // TODO: can we use fromEntityElement already here if there is a multi-type map?
                        if (fromEntityElement > 0) {
                            sb.append(" offset ");
                            sb.append(fromEntityElement);
                        }

                        AtlasSearchResult results = null;
                        try {
                            results = atlasRepositoryConnector.searchWithDSL(sb.toString());
                        } catch (AtlasServiceException e) {
                            raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.INVALID_SEARCH, methodName, e, sb.toString());
                        }
                        if (results != null) {
                            totalResults.add(results);
                        }
                    }
                }

            }
        }

        return combineMultipleResults(totalResults);

    }

    /**
     * Build an Atlas basic search based on the provided parameters, and return its results.
     *
     * @param entityTypeGUID unique identifier for the type of entity requested.  Null means any type of entity
     *                       (but could be slow so not recommended.
     * @param limitResultsByClassification name of a single classification by which to limit the results.
     * @param matchProperties Optional list of entity properties to match (contains wildcards), mutually-exclusive with
     *                        fullTextQuery.
     * @param matchCriteria Enum defining how the match properties should be matched to the classifications in the repository.
     * @param fullTextQuery Optional text that should be searched for in all text fields of the entities (mutually-exclusive
     *                      with matchProperties)
     * @param fromEntityElement the starting element number of the entities to return.
     *                                This is used when retrieving elements
     *                                beyond the first page of results. Zero means start from the first element.
     * @param limitResultsByStatus By default, entities in all statuses are returned.  However, it is possible
     *                             to specify a list of statuses (eg ACTIVE) to restrict the results to.  Null means all
     *                             status values.
     * @param pageSize the maximum number of result entities that can be returned on this request.  Zero means
     *                 unrestricted return results size.
     * @param userId the user through which to run the search
     * @return {@code List<EntityDetail>}
     * @throws FunctionNotSupportedException when attempting to search based on a status that is not supported in Atlas
     * @throws RepositoryErrorException when unable to run the search against Apache Atlas
     */
    private List<AtlasEntityHeader> buildAndRunBasicSearch(String methodName,
                                                           String entityTypeGUID,
                                                           String limitResultsByClassification,
                                                           InstanceProperties matchProperties,
                                                           MatchCriteria matchCriteria,
                                                           String fullTextQuery,
                                                           int fromEntityElement,
                                                           List<InstanceStatus> limitResultsByStatus,
                                                           int pageSize,
                                                           String userId) throws
            FunctionNotSupportedException,
            RepositoryErrorException {

        // Run multiple searches, if there are multiple types mapped to the OMRS type...
        Map<String, Map<String, Set<String>>> mappingsToSearch = getMappingsToSearch(entityTypeGUID, userId);
        List<AtlasSearchResult> totalResults = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> entryToSearch : mappingsToSearch.entrySet()) {
            String omrsTypeName = entryToSearch.getKey();
            Map<String, Set<String>> atlasTypeNamesByPrefix = entryToSearch.getValue();

            for (Map.Entry<String, Set<String>> entry : atlasTypeNamesByPrefix.entrySet()) {

                String prefix = entry.getKey();
                Set<String> atlasTypeNames = entry.getValue();

                for (String atlasTypeName : atlasTypeNames) {
                    // Otherwise Atlas's "basic" search is likely to be significantly faster
                    SearchParameters searchParameters = new SearchParameters();
                    if (atlasTypeName != null) {
                        searchParameters.setTypeName(atlasTypeName);
                    }
                    searchParameters.setIncludeClassificationAttributes(true);
                    searchParameters.setIncludeSubClassifications(true);
                    searchParameters.setIncludeSubTypes(true);
                    // TODO: can we use fromEntityElement already here if there is a multi-type map?
                    searchParameters.setOffset(fromEntityElement);
                    searchParameters.setLimit(pageSize);

                    Map<String, String> omrsPropertyMap = typeDefStore.getPropertyMappingsForOMRSTypeDef(omrsTypeName, prefix);
                    Map<String, TypeDefAttribute> omrsAttrTypeDefs = typeDefStore.getAllTypeDefAttributesForName(omrsTypeName);
                    List<SearchParameters.FilterCriteria> criteria = new ArrayList<>();

                    boolean skipSearch = false;
                    if (matchProperties != null) {
                        Map<String, InstancePropertyValue> properties = matchProperties.getInstanceProperties();
                        // By default, include only Referenceable's properties (as these will be the only properties that exist
                        // across ALL entity types)
                        if (properties != null) {
                            for (Map.Entry<String, InstancePropertyValue> property : properties.entrySet()) {
                                String omrsPropertyName = property.getKey();
                                InstancePropertyValue value = property.getValue();
                                boolean added = addSearchConditionFromValue(
                                        criteria,
                                        omrsPropertyName,
                                        value,
                                        omrsPropertyMap,
                                        omrsAttrTypeDefs,
                                        (matchCriteria != null) && matchCriteria.equals(MatchCriteria.NONE),
                                        false
                                );
                                if (!added) {
                                    if (matchCriteria == null || matchCriteria.equals(MatchCriteria.ALL)) {
                                        // If we are asked to find everything, but one of the properties cannot be searched,
                                        // then we should skip the search
                                        skipSearch = true;
                                        log.info("Skipping search ({}) -- ALL criteria should match, but this one is unmapped: {}", entityTypeGUID, omrsPropertyName);
                                    }
                                }
                            }
                        }
                    } else if (fullTextQuery != null) {

                        // Note that while it would be great to use the 'setQuery' of Atlas for this, it unfortunately does
                        // not work for all kinds of cases where things like '.' or '/' are involved (which even appear in
                        // Atlas's own sample metadata for properties like qualifiedName). Therefore we must do an OR-based
                        // search explicitly across all string-based properties.

                        // Setup a new PrimitivePropertyValue for the full text itself, that we can use for all of the
                        // various string attributes
                        PrimitivePropertyValue primitivePropertyValue = new PrimitivePropertyValue();
                        primitivePropertyValue.setPrimitiveDefCategory(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING);
                        primitivePropertyValue.setPrimitiveValue(fullTextQuery);
                        primitivePropertyValue.setTypeName(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING.getName());
                        primitivePropertyValue.setTypeGUID(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING.getGUID());

                        // Iterate through all of the string attributes, and for any that are actually mapped to OMRS
                        // add a search condition for them
                        for (Map.Entry<String, TypeDefAttribute> mapEntry : omrsAttrTypeDefs.entrySet()) {
                            String omrsPropertyName = mapEntry.getKey();
                            TypeDefAttribute typeDefAttribute = mapEntry.getValue();
                            log.debug("Considering attribute: {}", omrsPropertyName);
                            AttributeTypeDef attributeTypeDef = typeDefAttribute.getAttributeType();
                            if (attributeTypeDef.getCategory().equals(AttributeTypeDefCategory.PRIMITIVE)) {
                                PrimitiveDef primitiveDef = (PrimitiveDef) attributeTypeDef;
                                if (primitiveDef.getPrimitiveDefCategory().equals(PrimitiveDefCategory.OM_PRIMITIVE_TYPE_STRING)) {
                                    log.debug(" ... attribute is a String, continuing ...");
                                    if (omrsPropertyMap.containsKey(omrsPropertyName)) {
                                        String atlasPropertyName = omrsPropertyMap.get(omrsPropertyName);
                                        log.debug(" ... attribute is mapped, to: {}", atlasPropertyName);
                                        if (atlasPropertyName != null) {
                                            log.debug(" ... adding criterion for value: {}", primitivePropertyValue);
                                            boolean added = addSearchConditionFromValue(
                                                    criteria,
                                                    omrsPropertyName,
                                                    primitivePropertyValue,
                                                    omrsPropertyMap,
                                                    omrsAttrTypeDefs,
                                                    (matchCriteria != null) && matchCriteria.equals(MatchCriteria.NONE),
                                                    false
                                            );
                                            if (!added) {
                                                if (matchCriteria == null || matchCriteria.equals(MatchCriteria.ALL)) {
                                                    // If we are asked to find everything, but one of the properties cannot be searched,
                                                    // then we should skip the search
                                                    skipSearch = true;
                                                    log.info("Skipping search ({}) -- ALL criteria should match, but this one is unmapped: {}", entityTypeGUID, omrsPropertyName);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SearchParameters.FilterCriteria entityFilters = new SearchParameters.FilterCriteria();
                    if (criteria.size() > 1) {
                        entityFilters.setCriterion(criteria);
                        if (matchCriteria != null) {
                            // If matchCriteria were provided, use them
                            switch (matchCriteria) {
                                case ALL:
                                case NONE:
                                    entityFilters.setCondition(SearchParameters.FilterCriteria.Condition.AND);
                                    break;
                                case ANY:
                                    entityFilters.setCondition(SearchParameters.FilterCriteria.Condition.OR);
                                    break;
                            }
                        } else if (fullTextQuery != null) {
                            // If none were provided, but a fullTextQuery was, we should use an OR-based semantic
                            entityFilters.setCondition(SearchParameters.FilterCriteria.Condition.OR);
                        } else {
                            // Otherwise we should default to an AND-based semantic
                            entityFilters.setCondition(SearchParameters.FilterCriteria.Condition.AND);
                        }
                    } else if (criteria.size() == 1) {
                        entityFilters = criteria.get(0);
                    }
                    searchParameters.setEntityFilters(entityFilters);

                    if (limitResultsByStatus != null) {
                        Set<InstanceStatus> limitSet = new HashSet<>(limitResultsByStatus);
                        if (limitSet.equals(availableStates) || (limitSet.size() == 1 && limitSet.contains(InstanceStatus.DELETED))) {
                            // If we're to search for deleted, do not exclude deleted
                            searchParameters.setExcludeDeletedEntities(false);
                        } else if (limitSet.size() == 1 && limitSet.contains(InstanceStatus.ACTIVE)) {
                            // Otherwise if we are only after active, do exclude deleted
                            searchParameters.setExcludeDeletedEntities(true);
                        } else if (!limitSet.isEmpty()) {
                            // Otherwise we must be searching only for states that Atlas does not support, so we should ensure
                            // that no results are returned (by skipping the search entirely).
                            skipSearch = true;
                            log.info("Skipping search ({}) -- searching for unsupported states: {}", entityTypeGUID, limitSet);
                        }
                    }

                    if (!skipSearch) {

                        if (limitResultsByClassification != null) {
                            searchParameters.setClassification(limitResultsByClassification);
                        }

                        AtlasSearchResult results = null;
                        try {
                            results = atlasRepositoryConnector.searchForEntities(searchParameters);
                        } catch (AtlasServiceException e) {
                            raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.INVALID_SEARCH, methodName, e, searchParameters.toString());
                        }
                        if (results != null) {
                            totalResults.add(results);
                        }

                    }

                }

            }

        }

        return combineMultipleResults(totalResults);

    }

    /**
     * Combine a list of Apache Atlas results into a single list of atlas entities.
     *
     * @param resultsList the list of multiple Apache Atlas search results
     * @return {@code List<AtlasEntityHeader>}
     */
    private List<AtlasEntityHeader> combineMultipleResults(List<AtlasSearchResult> resultsList) {
        if (resultsList == null || resultsList.isEmpty()) {
            return null;
        } else if (resultsList.size() == 1) {
            return resultsList.get(0).getEntities();
        } else {
            List<AtlasEntityHeader> totalResults = new ArrayList<>();
            for (AtlasSearchResult result : resultsList) {
                if (result != null) {
                    List<AtlasEntityHeader> entityResults = result.getEntities();
                    if (entityResults != null) {
                        totalResults.addAll(entityResults);
                    }
                }
            }
            return totalResults;
        }
    }

    /**
     * Sort the list of results and limit based on the provided parameters.
     *
     * @param results the Apache Atlas results to sort and limit
     * @param entityTypeGUID the type of entity that was requested (or null for all)
     * @param fromElement the starting element to include in the limited results
     * @param sequencingProperty the property by which to sort the results (or null, if not sorting by property)
     * @param sequencingOrder the order by which to sort the results
     * @param pageSize the number of results to include in this page
     * @param userId the user through which to translate the results
     * @return
     * @throws InvalidParameterException the guid is null.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                  the metadata collection is stored.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.
     */
    private List<EntityDetail> sortAndLimitFinalResults(List<AtlasEntityHeader> results,
                                                        String entityTypeGUID,
                                                        int fromElement,
                                                        String sequencingProperty,
                                                        SequencingOrder sequencingOrder,
                                                        int pageSize,
                                                        String userId) throws
            InvalidParameterException,
            RepositoryErrorException,
            UserNotAuthorizedException {

        // If no entity type GUID was provided (search was done with 'null' originally for all types), then set it here
        // to the GUID for Referenceable, so that we can properly do subtype checking in the subsequent steps.
        if (entityTypeGUID == null) {
            entityTypeGUID = typeDefStore.getTypeDefByName("Referenceable").getGUID();
        }

        List<EntityDetail> totalResults = new ArrayList<>(getEntityDetailsFromAtlasResults(results, entityTypeGUID, userId));

        // TODO: send something in that determines whether re-sorting the results is actually necessary?
        // Need to potentially re-sort and re-limit the results, if we ran the search against more than one type
        Comparator<EntityDetail> comparator = SequencingUtils.getEntityDetailComparator(sequencingOrder, sequencingProperty);
        if (comparator != null) {
            totalResults.sort(comparator);
        }
        int endOfPageMarker = Math.min(fromElement + pageSize, totalResults.size());
        if (fromElement != 0 || endOfPageMarker < totalResults.size()) {
            totalResults = totalResults.subList(fromElement, endOfPageMarker);
        }

        return totalResults;

    }

    /**
     * Retrieves a list of EntityDetail objects given a list of AtlasEntityHeader objects.
     *
     * @param atlasEntities the Atlas entities for which to retrieve details
     * @param entityTypeGUID the type of entity that was requested (or null for all)
     * @param userId the user through which to do the retrieval
     * @return {@code List<EntityDetail>}
     * @throws InvalidParameterException the guid is null.
     * @throws RepositoryErrorException there is a problem communicating with the metadata repository where
     *                                  the metadata collection is stored.
     * @throws UserNotAuthorizedException the userId is not permitted to perform this operation.
     */
    private List<EntityDetail> getEntityDetailsFromAtlasResults(List<AtlasEntityHeader> atlasEntities,
                                                                String entityTypeGUID,
                                                                String userId) throws
            InvalidParameterException,
            RepositoryErrorException,
            UserNotAuthorizedException {

        List<EntityDetail> entityDetails = new ArrayList<>();

        // If requested type is for a generated type, we need to retrieve it differently -- i.e. prefix the GUID
        // ... so we should determine what prefixes to use based on the requested entity type GUID
        Set<String> prefixes = new HashSet<>();
        Map<String, Map<String, Set<String>>> mappings = getMappingsToSearch(entityTypeGUID, userId);
        for (Map<String, Set<String>> atlasTypeNamesByPrefix : mappings.values()) {
            prefixes.addAll(atlasTypeNamesByPrefix.keySet());
        }

        if (atlasEntities != null) {
            for (AtlasEntityHeader atlasEntityHeader : atlasEntities) {
                for (String prefix : prefixes) {
                    try {
                        AtlasGuid guid = new AtlasGuid(atlasEntityHeader.getGuid(), prefix);
                        // ... and retrieve the entity itself only based on those types (prefixes) that have been requested
                        EntityDetail detail = getEntityDetail(userId, guid.toString());
                        // Depending on prefix, this could come back with results that should not be included
                        // (ie. for generated types or non-generated types, depending on requested entityTypeGUID),
                        // so only include those that were requested
                        if (detail != null) {
                            String typeName = detail.getType().getTypeDefName();
                            try {
                                TypeDef typeDef = repositoryHelper.getTypeDef(repositoryName, "entityTypeGUID", entityTypeGUID, "getEntityDetailsFromAtlasResults");
                                if (repositoryHelper.isTypeOf(repositoryName, typeName, typeDef.getName())) {
                                    entityDetails.add(detail);
                                }
                            } catch (TypeErrorException e) {
                                log.error("Unable to find any TypeDef for entityTypeGUID: {}", entityTypeGUID);
                            }
                        } else {
                            log.error("Entity with GUID {} not mapped -- excluding from results.", atlasEntityHeader.getGuid());
                        }
                    } catch (EntityNotKnownException e) {
                        log.error("Entity with GUID {} not known -- excluding from results.", atlasEntityHeader.getGuid());
                    }
                }
            }
        }

        return entityDetails;

    }

    /**
     * Adds the provided value to the search criteria for Apache Atlas, returning whether it was able to add the
     * condition (true) or not (false).
     *
     * @param criteria the search criteria to which to append
     * @param omrsPropertyName the OMRS property name to search
     * @param value the value for which to search
     * @param omrsToAtlasPropertyMap the mappings from OMRS property name to Atlas property name
     * @param omrsTypeDefAttrMap the mappings from OMRS property name to TypeDefAttribute definition of the property
     * @param negateCondition if true, negate (invert) the condition / operator
     * @return boolean indicating whether the condition could be added (true) or not (false)
     * @throws FunctionNotSupportedException when a regular expression is used for the search that is not supported
     */
    @SuppressWarnings("unchecked")
    private <T> boolean addSearchConditionFromValue(List<T> criteria,
                                                    String omrsPropertyName,
                                                    InstancePropertyValue value,
                                                    Map<String, String> omrsToAtlasPropertyMap,
                                                    Map<String, TypeDefAttribute> omrsTypeDefAttrMap,
                                                    boolean negateCondition,
                                                    boolean dslQuery) throws FunctionNotSupportedException {

        final String methodName = "addSearchConditionFromValue";
        boolean added = true;

        if (omrsPropertyName != null) {
            if (omrsToAtlasPropertyMap != null) {
                String atlasPropertyName = omrsToAtlasPropertyMap.get(omrsPropertyName);
                if (atlasPropertyName != null) {

                    SearchParameters.FilterCriteria atlasCriterion = new SearchParameters.FilterCriteria();
                    StringBuilder sbCriterion = new StringBuilder();
                    InstancePropertyCategory category = value.getInstancePropertyCategory();
                    switch (category) {
                        case PRIMITIVE:
                            PrimitivePropertyValue actualValue = (PrimitivePropertyValue) value;
                            PrimitiveDefCategory primitiveType = actualValue.getPrimitiveDefCategory();
                            switch (primitiveType) {
                                case OM_PRIMITIVE_TYPE_BOOLEAN:
                                case OM_PRIMITIVE_TYPE_SHORT:
                                case OM_PRIMITIVE_TYPE_INT:
                                case OM_PRIMITIVE_TYPE_LONG:
                                case OM_PRIMITIVE_TYPE_FLOAT:
                                case OM_PRIMITIVE_TYPE_DOUBLE:
                                case OM_PRIMITIVE_TYPE_BIGINTEGER:
                                case OM_PRIMITIVE_TYPE_BIGDECIMAL:
                                    String nonString = actualValue.getPrimitiveValue().toString();
                                    atlasCriterion.setAttributeName(atlasPropertyName);
                                    sbCriterion.append(atlasPropertyName);
                                    if (negateCondition) {
                                        atlasCriterion.setOperator(SearchParameters.Operator.NEQ);
                                        sbCriterion.append(" != ");
                                    } else {
                                        atlasCriterion.setOperator(SearchParameters.Operator.EQ);
                                        sbCriterion.append(" = ");
                                    }
                                    atlasCriterion.setAttributeValue(nonString);
                                    sbCriterion.append(nonString);
                                    if (dslQuery) {
                                        criteria.add((T) sbCriterion.toString());
                                    } else {
                                        criteria.add((T) atlasCriterion);
                                    }
                                    break;
                                case OM_PRIMITIVE_TYPE_BYTE:
                                case OM_PRIMITIVE_TYPE_CHAR:
                                    String single = actualValue.getPrimitiveValue().toString();
                                    atlasCriterion.setAttributeName(atlasPropertyName);
                                    sbCriterion.append(atlasPropertyName);
                                    if (negateCondition) {
                                        atlasCriterion.setOperator(SearchParameters.Operator.NEQ);
                                        sbCriterion.append(" != \"");
                                    } else {
                                        atlasCriterion.setOperator(SearchParameters.Operator.EQ);
                                        sbCriterion.append(" = \"");
                                    }
                                    atlasCriterion.setAttributeValue(single);
                                    sbCriterion.append(single);
                                    sbCriterion.append("\"");
                                    if (dslQuery) {
                                        criteria.add((T) sbCriterion.toString());
                                    } else {
                                        criteria.add((T) atlasCriterion);
                                    }
                                    break;
                                case OM_PRIMITIVE_TYPE_DATE:
                                    Long epoch = (Long) actualValue.getPrimitiveValue();
                                    String formattedDate = atlasDateFormat.format(new Date(epoch));
                                    atlasCriterion.setAttributeName(atlasPropertyName);
                                    if (atlasPropertyName.equals("createTime")) {
                                        sbCriterion.append("__timestamp");
                                    } else if (atlasPropertyName.equals("updateTime")) {
                                        sbCriterion.append("__modificationTimestamp");
                                    } else {
                                        sbCriterion.append(atlasPropertyName);
                                    }
                                    if (negateCondition) {
                                        atlasCriterion.setOperator(SearchParameters.Operator.NEQ);
                                        sbCriterion.append(" != \"");
                                    } else {
                                        atlasCriterion.setOperator(SearchParameters.Operator.EQ);
                                        sbCriterion.append(" = \"");
                                    }
                                    atlasCriterion.setAttributeValue(formattedDate);
                                    sbCriterion.append(epoch);
                                    sbCriterion.append("\"");
                                    if (dslQuery) {
                                        criteria.add((T) sbCriterion.toString());
                                    } else {
                                        criteria.add((T) atlasCriterion);
                                    }
                                    break;
                                case OM_PRIMITIVE_TYPE_STRING:
                                default:
                                    atlasCriterion.setAttributeName(atlasPropertyName);
                                    sbCriterion.append(atlasPropertyName);
                                    String candidateValue = actualValue.getPrimitiveValue().toString();
                                    String unqualifiedValue = repositoryHelper.getUnqualifiedLiteralString(candidateValue);
                                    if (repositoryHelper.isContainsRegex(candidateValue)) {
                                        sbCriterion.append(" LIKE \"*");
                                        sbCriterion.append(unqualifiedValue);
                                        sbCriterion.append("\"*");
                                        atlasCriterion.setOperator(SearchParameters.Operator.CONTAINS);
                                    } else if (repositoryHelper.isEndsWithRegex(candidateValue)) {
                                        sbCriterion.append(" LIKE \"*");
                                        sbCriterion.append(unqualifiedValue);
                                        sbCriterion.append("\"");
                                        atlasCriterion.setOperator(SearchParameters.Operator.ENDS_WITH);
                                    } else if (repositoryHelper.isStartsWithRegex(candidateValue)) {
                                        sbCriterion.append(" LIKE \"");
                                        sbCriterion.append(unqualifiedValue);
                                        sbCriterion.append("*\"");
                                        atlasCriterion.setOperator(SearchParameters.Operator.STARTS_WITH);
                                    } else if (repositoryHelper.isExactMatchRegex(candidateValue)) {
                                        if (negateCondition) {
                                            if (unqualifiedValue.length() == 0) {
                                                atlasCriterion.setOperator(SearchParameters.Operator.NOT_NULL);
                                            } else {
                                                atlasCriterion.setOperator(SearchParameters.Operator.NEQ);
                                            }
                                            sbCriterion.append(" != \"");
                                        } else {
                                            if (unqualifiedValue.length() == 0) {
                                                atlasCriterion.setOperator(SearchParameters.Operator.IS_NULL);
                                            } else {
                                                atlasCriterion.setOperator(SearchParameters.Operator.EQ);
                                            }
                                            sbCriterion.append(" = \"");
                                        }
                                        sbCriterion.append(unqualifiedValue);
                                        sbCriterion.append("\"");
                                    } else {
                                        raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode.REGEX_NOT_IMPLEMENTED, methodName, repositoryName, candidateValue);
                                    }
                                    atlasCriterion.setAttributeValue(unqualifiedValue);
                                    if (dslQuery) {
                                        criteria.add((T) sbCriterion.toString());
                                    } else {
                                        criteria.add((T) atlasCriterion);
                                    }
                                    break;
                            }
                            break;
                        case ENUM:
                            String omrsEnumValue = ((EnumPropertyValue) value).getSymbolicName();
                            TypeDefAttribute typeDefAttribute = omrsTypeDefAttrMap.get(omrsPropertyName);
                            if (typeDefAttribute != null) {
                                Map<String, Set<String>> elementMap = attributeTypeDefStore.getElementMappingsForOMRSTypeDef(typeDefAttribute.getAttributeType().getName());
                                if (elementMap != null) {
                                    Set<String> atlasEnumValues = elementMap.get(omrsEnumValue);
                                    if (atlasEnumValues != null && !atlasEnumValues.isEmpty()) {
                                        // build a list of the OR-able sub-conditions
                                        List<SearchParameters.FilterCriteria> subAtlasCriteria = new ArrayList<>();
                                        List<String> subCriteria = new ArrayList<>();
                                        sbCriterion.append("(");
                                        for (String atlasEnumValue : atlasEnumValues) {
                                            StringBuilder subCriterion = new StringBuilder();
                                            SearchParameters.FilterCriteria subAtlasCriterion = new SearchParameters.FilterCriteria();
                                            subAtlasCriterion.setAttributeName(atlasPropertyName);
                                            subCriterion.append(atlasPropertyName);
                                            if (negateCondition) {
                                                subAtlasCriterion.setOperator(SearchParameters.Operator.NEQ);
                                                subCriterion.append(" != \"");
                                            } else {
                                                subAtlasCriterion.setOperator(SearchParameters.Operator.EQ);
                                                subCriterion.append(" = \"");
                                            }
                                            subAtlasCriterion.setAttributeValue(atlasEnumValue);
                                            subCriterion.append(atlasEnumValue);
                                            subCriterion.append("\"");
                                            subAtlasCriteria.add(subAtlasCriterion);
                                            subCriteria.add(subCriterion.toString());
                                        }
                                        sbCriterion.append(String.join(" OR ", subCriteria));
                                        sbCriterion.append(")");
                                        atlasCriterion.setCriterion(subAtlasCriteria);
                                        atlasCriterion.setCondition(SearchParameters.FilterCriteria.Condition.OR);
                                        if (dslQuery) {
                                            criteria.add((T) sbCriterion.toString());
                                        } else {
                                            criteria.add((T) atlasCriterion);
                                        }
                                    } else {
                                        log.warn("Unable to find mapped enum value for {}: {}", omrsPropertyName, omrsEnumValue);
                                        added = false;
                                    }
                                }
                            } else {
                                log.warn("Unable to find enum with name: {}", omrsPropertyName);
                                added = false;
                            }
                            break;
                    /*case STRUCT:
                        Map<String, InstancePropertyValue> structValues = ((StructPropertyValue) value).getAttributes().getInstanceProperties();
                        for (Map.Entry<String, InstancePropertyValue> nextEntry : structValues.entrySet()) {
                            addSearchConditionFromValue(
                                    igcSearchConditionSet,
                                    nextEntry.getKey(),
                                    igcProperties,
                                    mapping,
                                    nextEntry.getValue()
                            );
                        }
                        break;*/
                        case MAP:
                            Map<String, InstancePropertyValue> mapValues = ((MapPropertyValue) value).getMapValues().getInstanceProperties();
                            for (Map.Entry<String, InstancePropertyValue> nextEntry : mapValues.entrySet()) {
                                added = added && addSearchConditionFromValue(
                                        criteria,
                                        nextEntry.getKey(),
                                        nextEntry.getValue(),
                                        omrsToAtlasPropertyMap,
                                        omrsTypeDefAttrMap,
                                        negateCondition,
                                        dslQuery
                                );
                            }
                            break;
                        case ARRAY:
                            Map<String, InstancePropertyValue> arrayValues = ((ArrayPropertyValue) value).getArrayValues().getInstanceProperties();
                            for (Map.Entry<String, InstancePropertyValue> nextEntry : arrayValues.entrySet()) {
                                added = added && addSearchConditionFromValue(
                                        criteria,
                                        atlasPropertyName,
                                        nextEntry.getValue(),
                                        omrsToAtlasPropertyMap,
                                        omrsTypeDefAttrMap,
                                        negateCondition,
                                        dslQuery
                                );
                            }
                            break;
                        default:
                            // Do nothing
                            log.warn("Unable to handle search criteria for value type: {}", category);
                            added = false;
                            break;
                    }

                } else {
                    log.warn("Unable to add search condition, no mapped Atlas property for '{}': {}", omrsPropertyName, value);
                    added = false;
                }
            } else {
                log.info("Unable to add search condition, no mapped Atlas property for property: {}", omrsPropertyName);
                added = false;
            }
        } else {
            log.warn("Unable to add search condition, no OMRS property: {}", value);
            added = false;
        }

        return added;

    }

    /**
     * Compare the provided OMRS TypeDef to the provided Apache Atlas TypeDef and ensure they fully cover each other.
     *
     * @param omrsTypeDef the OMRS TypeDef to compare
     * @param atlasTypeDef the Apache Atlas TypeDef to compare
     * @return {@code List<String>} of issues identified, or null if types have full coverage

    private List<String> validateTypeDefCoverage(TypeDef omrsTypeDef,
                                                 AtlasStructDef atlasTypeDef) {

        List<String> issues = new ArrayList<>();

        // Validate support for same statuses
        Set<InstanceStatus> validStatuses = new HashSet<>(omrsTypeDef.getValidInstanceStatusList());
        if (!validStatuses.equals(availableStates)) {
            issues.add("not all statuses supported: " + validStatuses);
        }

        // Validate that we support all of the possible properties
        validateAttributeCoverage(omrsTypeDef, atlasTypeDef, issues);

        return issues.isEmpty() ? null : issues;

    }
     */

    /**
     * Compare the provided OMRS TypeDef's properties to the provided Apache Atlas TypeDef's properties and ensure they
     * fully cover each other (including across OMRS TypeDef's supertype hierarchy).
     *
     * @param omrsTypeDef the OMRS TypeDef to compare
     * @param atlasTypeDef the Apache Atlas TypeDef to compare
     * @param issues a list of issues to append to if any gaps are found

    private void validateAttributeCoverage(TypeDef omrsTypeDef,
                                           AtlasStructDef atlasTypeDef,
                                           List<String> issues) {

        String omrsTypeDefName = omrsTypeDef.getName();
        List<TypeDefAttribute> omrsProperties = omrsTypeDef.getPropertiesDefinition();
        if (omrsProperties != null) {
            // This should give us back a one-to-one mapping in cases where the typedef is OMRS-created in Atlas
            Map<String, String> mappedProperties = typeDefStore.getPropertyMappingsForOMRSTypeDef(omrsTypeDefName);
            for (TypeDefAttribute typeDefAttribute : omrsProperties) {
                String omrsPropertyName = typeDefAttribute.getAttributeName();
                if (mappedProperties != null && !mappedProperties.containsKey(omrsPropertyName)) {
                    issues.add("property '" + omrsPropertyName + "' is not mapped");
                } else if (mappedProperties != null) {
                    String atlasPropertyName = mappedProperties.get(omrsPropertyName);
                    if (atlasTypeDef.getAttribute(atlasPropertyName) == null) {
                        issues.add("property '" + atlasPropertyName + "' not found in Atlas type definition");
                    }
                } else {
                    if (atlasTypeDef.getAttribute(omrsPropertyName) == null) {
                        issues.add("property '" + omrsPropertyName + "' not found in Atlas type definition");
                    }
                }
            }
        }
        TypeDefLink superType = omrsTypeDef.getSuperType();
        if (superType != null) {
            TypeDef superTypeDef = typeDefStore.getTypeDefByGUID(superType.getGUID(), false);
            if (superTypeDef == null) {
                superTypeDef = typeDefStore.getUnimplementedTypeDefByGUID(superType.getGUID());
            }
            if (superType != null) {
                validateAttributeCoverage(superTypeDef, atlasTypeDef, issues);
            }
        }

    }
     */

    /**
     * Try to retrieve an Atlas entity using the provided GUID, and if not found throw an EntityNotKnownException.
     * @param guid the GUID for the entity to retrieve
     * @param methodName the name of the method retrieving the entity
     * @return AtlasEntityWithExtInfo
     * @throws EntityNotKnownException if the entity cannot be found in Atlas
     */
    private AtlasEntity.AtlasEntityWithExtInfo getAtlasEntitySafe(String guid, String methodName) throws EntityNotKnownException {
        AtlasEntity.AtlasEntityWithExtInfo entity = null;
        try {
            entity = this.atlasRepositoryConnector.getEntityByGUID(guid, false, true);
        } catch (AtlasServiceException e) {
            raiseEntityNotKnownException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, guid, methodName, repositoryName);
        }
        if (entity == null) {
            raiseEntityNotKnownException(ApacheAtlasOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, null, guid, methodName, repositoryName);
        }
        return entity;
    }

    /**
     * Throw an EntityNotKnownException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any parameters for formatting the error message
     * @throws EntityNotKnownException always
     */
    private void raiseEntityNotKnownException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws EntityNotKnownException {
        if (cause == null) {
            throw new EntityNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new EntityNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

    /**
     * Throw a RelationshipNotKnownException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any parameters for formatting the error message
     * @throws RelationshipNotKnownException always
     */
    private void raiseRelationshipNotKnownException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws RelationshipNotKnownException {
        if (cause == null) {
            throw new RelationshipNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new RelationshipNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
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

    /**
     * Throw a TypeDefNotKnownException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any parameters for formatting the error message
     * @throws TypeDefNotKnownException always
     */
    private void raiseTypeDefNotKnownException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws TypeDefNotKnownException {
        if (cause == null) {
            throw new TypeDefNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new TypeDefNotKnownException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

    /**
     * Throw a TypeDefNotSupportedException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any parameters for formatting the error message
     * @throws TypeDefNotSupportedException always
     */
    private void raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws TypeDefNotSupportedException {
        if (cause == null) {
            throw new TypeDefNotSupportedException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new TypeDefNotSupportedException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

    /**
     * Throw a FunctionNotSupportedException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param params any parameters for formatting the error message
     * @throws FunctionNotSupportedException always
     */
    private void raiseFunctionNotSupportedException(ApacheAtlasOMRSErrorCode errorCode, String methodName, String ...params) throws FunctionNotSupportedException {
        throw new FunctionNotSupportedException(errorCode.getMessageDefinition(params),
                this.getClass().getName(),
                methodName);
    }

}
