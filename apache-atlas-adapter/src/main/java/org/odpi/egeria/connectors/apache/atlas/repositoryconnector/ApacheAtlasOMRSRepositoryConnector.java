/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.SearchFilter;
import org.apache.atlas.model.discovery.AtlasSearchResult;
import org.apache.atlas.model.discovery.SearchParameters;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasRelationship;
import org.apache.atlas.model.typedef.AtlasEntityDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSAuditCode;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;

import org.apache.atlas.AtlasClientV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApacheAtlasOMRSRepositoryConnector extends OMRSRepositoryConnector {

    private static final Logger log = LoggerFactory.getLogger(ApacheAtlasOMRSRepositoryConnector.class);

    public static final String EP_ENTITY = "/api/atlas/v2/entity/guid/";

    private String url;
    private AtlasClientV2 atlasClient;
    private Map<String, AtlasEntityDef> atlasEntityTypesByName;

    private boolean purgeForDelete = false;
    private boolean successfulInit = false;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public ApacheAtlasOMRSRepositoryConnector() {
        atlasEntityTypesByName = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OMRSMetadataCollection getMetadataCollection() throws RepositoryErrorException {
        final String methodName = "getMetadataCollection";
        if (metadataCollection == null) {
            // If the metadata collection has not yet been created, attempt to create it now
            try {
                connectToAtlas(methodName);
            } catch (ConnectorCheckedException e) {
                raiseRepositoryErrorException(ApacheAtlasOMRSErrorCode.REST_CLIENT_FAILURE, methodName, e, getServerName());
            }
        }
        return super.getMetadataCollection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";

        auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.REPOSITORY_SERVICE_STARTING.getMessageDefinition());

        if (metadataCollection == null) {
            // If the metadata collection has not yet been created, attempt to create it now
            connectToAtlas(methodName);
        }

        auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.REPOSITORY_SERVICE_STARTED.getMessageDefinition(getServerName()));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void disconnect() {
        final String methodName = "disconnect";
        auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.REPOSITORY_SERVICE_SHUTDOWN.getMessageDefinition(getServerName()));
    }

    /**
     * Retrieve the base URL of the Apache Atlas environment.
     *
     * @return String
     */
    public String getBaseURL() {
        return this.url;
    }

    /**
     * Indicates whether a purge event should be sent in the case of a delete being received (true) or
     * simply to propagate a delete (false). Default is to propagate delete as a delete.
     *
     * @return boolean
     */
    public boolean sendPurgeForDelete() {
        return this.purgeForDelete;
    }

    /**
     * Indicates whether the provided TypeDef exists in this Apache Atlas environment.
     *
     * @param name the name of the TypeDef in Apache Atlas to check
     * @return boolean
     */
    public boolean typeDefExistsByName(String name) {
        return atlasClient.typeWithNameExists(name);
    }

    /**
     * Retrieves the Apache Atlas typedef specified from the Apache Atlas environment.
     *
     * @param name the name of the TypeDef to retrieve
     * @param typeDefCategory the type (in OMRS terms) of the TypeDef to retrieve
     * @return AtlasStructDef
     * @throws AtlasServiceException if there is any problem retrieving the type definition

    public AtlasStructDef getTypeDefByName(String name, TypeDefCategory typeDefCategory) throws AtlasServiceException {

        AtlasStructDef result = null;
        switch(typeDefCategory) {
            case CLASSIFICATION_DEF:
                result = atlasClient.getClassificationDefByName(name);
                break;
            case ENTITY_DEF:
                result = atlasClient.getEntityDefByName(name);
                break;
            case RELATIONSHIP_DEF:
                // For whatever reason, relationshipdef retrieval is not in the Atlas client, so writing our own
                // API call for this one
                String atlasPath = "relationshipdef";
                AtlasBaseClient.API api = new AtlasBaseClient.API(String.format(AtlasClientV2.TYPES_API + "%s/name/%s", atlasPath, name), HttpMethod.GET, Response.Status.OK);
                result = atlasClient.callAPI(api, AtlasRelationshipDef.class, null);
                break;
            default:
                break;
        }
        return result;

    }*/

    /**
     * Retrieves an Apache Atlas Entity instance by its GUID, including all of its relationships.
     *
     * @param guid the GUID of the entity instance to retrieve
     * @return AtlasEntityWithExtInfo
     * @throws AtlasServiceException if there is any error retrieving the entity

    public AtlasEntity.AtlasEntityWithExtInfo getEntityByGUID(String guid) throws AtlasServiceException {
        return getEntityByGUID(guid, false, true);
    }*/

    /**
     * Retrieve an Apache Atlas Entity instance by its GUID.
     *
     * @param guid the GUID of the entity instance to retrieve
     * @param minimalExtraInfo if true, minimize the amount of extra information retrieved about the GUID
     * @param ignoreRelationships if true, will return only the entity (none of its relationships)
     * @return AtlasEntityWithExtInfo
     * @throws AtlasServiceException if there is any error retrieving the entity
     */
    public AtlasEntity.AtlasEntityWithExtInfo getEntityByGUID(String guid,
                                                              boolean minimalExtraInfo,
                                                              boolean ignoreRelationships) throws AtlasServiceException {
        return atlasClient.getEntityByGuid(guid, minimalExtraInfo, ignoreRelationships);
    }

    /**
     * Retrieves an Apache Atlas Relationship instance by its GUID.
     *
     * @param guid the GUID of the relationship instance to retrieve
     * @return AtlasRelationshipWithExtInfo
     * @throws AtlasServiceException if there is any error retrieving the relationship
     */
    public AtlasRelationship.AtlasRelationshipWithExtInfo getRelationshipByGUID(String guid) throws AtlasServiceException {
        return getRelationshipByGUID(guid, false);
    }

    /**
     * Retrieves an Apache Atlas Relationship instance by its GUID.
     *
     * @param guid the GUID of the relationship instance to retrieve
     * @param extendedInfo if true, will include extended info in the result
     * @return AtlasRelationshipWithExtInfo
     * @throws AtlasServiceException if there is any error retrieving the relationship
     */
    public AtlasRelationship.AtlasRelationshipWithExtInfo getRelationshipByGUID(String guid,
                                                                                boolean extendedInfo) throws AtlasServiceException {
        return atlasClient.getRelationshipByGuid(guid, extendedInfo);
    }

    /**
     * Adds the list of TypeDefs provided to Apache Atlas.
     *
     * @param typeDefs the TypeDefs to add to Apache Atlas
     * @return AtlasTypesDef
     * @throws AtlasServiceException if there is any error creating the type definition
     */
    public AtlasTypesDef createTypeDef(AtlasTypesDef typeDefs) throws AtlasServiceException {
        return atlasClient.createAtlasTypeDefs(typeDefs);
    }

    /**
     * Updates the list of TypeDefs provided in Apache Atlas.
     *
     * @param typeDefs the TypeDefs to update in Apache Atlas
     * @return AtlasTypesDef
     * @throws AtlasServiceException if there is any error applying the update
     */
    public AtlasTypesDef updateTypeDef(AtlasTypesDef typeDefs) throws AtlasServiceException {
        return atlasClient.updateAtlasTypeDefs(typeDefs);
    }

    /**
     * Search for entities based on the provided parameters.
     *
     * @param searchParameters the criteria by which to search
     * @return AtlasSearchResult
     * @throws AtlasServiceException if there is any error retrieving the relationship
     */
    public AtlasSearchResult searchForEntities(SearchParameters searchParameters) throws AtlasServiceException {
        log.debug("Searching Atlas with: {}", searchParameters);
        return atlasClient.facetedSearch(searchParameters);
    }

    /**
     * Search for entities based one provided DSL query string.
     *
     * @param dslQuery the query to use for the search
     * @return AtlasSearchResult
     * @throws AtlasServiceException if there is any error retrieving the relationship
     */
    public AtlasSearchResult searchWithDSL(String dslQuery) throws AtlasServiceException {
        log.debug("Searching Atlas with: {}", dslQuery);
        return atlasClient.dslSearch(dslQuery);
    }

    /**
     * Resolve the parent type for the provided Apache Atlas entity type.
     *
     * @param type the Apache Atlas entity type
     * @return String the Apache Atlas parent entity type
     */
    public String getParentTypeForAtlasEntityType(String type) {
        AtlasEntityDef atlasEntityDef = atlasEntityTypesByName.getOrDefault(type, null);
        if (atlasEntityDef != null) {
            Set<String> superTypes = atlasEntityDef.getSuperTypes();
            if (superTypes != null && !superTypes.isEmpty()) {
                return superTypes.iterator().next();
            }
        }
        return null;
    }

    /**
     * Save the entity provided to Apache Atlas.
     *
     * @param atlasEntity the Apache Atlas entity to save
     * @param create indicates whether the entity should be created (true) or updated (false)
     * @return EntityMutationResponse listing the details of the entity that was saved
     * @throws AtlasServiceException if there is any error retrieving the relationship

    public EntityMutationResponse saveEntity(AtlasEntity.AtlasEntityWithExtInfo atlasEntity,
                                             boolean create) throws AtlasServiceException {
        EntityMutationResponse result;
        if (create) {
            result = atlasClient.createEntity(atlasEntity);
        } else {
            result = atlasClient.updateEntity(atlasEntity);
        }
        return result;
    }*/

    /**
     * Attempt to connect to the Apache Atlas server specified by the received parameters.
     *
     * @param methodName the method attempting to connect
     * @throws ConnectorCheckedException if there is any issue connecting
     */
    private void connectToAtlas(String methodName) throws ConnectorCheckedException {

        EndpointProperties endpointProperties = connectionProperties.getEndpoint();
        if (endpointProperties == null) {
            raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode.REST_CLIENT_FAILURE, methodName, null, "null");
        } else {

            this.url = endpointProperties.getProtocol() + "://" + endpointProperties.getAddress();

            auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.CONNECTING_TO_ATLAS.getMessageDefinition(getBaseURL()));

            String username = connectionProperties.getUserId();
            String password = connectionProperties.getClearPassword();

            this.atlasClient = new AtlasClientV2(new String[]{getBaseURL()}, new String[]{username, password});

            // Test REST API connection by attempting to retrieve types list
            AtlasTypesDef atlasTypes = null;
            try {
                atlasTypes = atlasClient.getAllTypeDefs(new SearchFilter());
                successfulInit = (atlasTypes != null && atlasTypes.hasEntityDef("Referenceable"));
            } catch (AtlasServiceException e) {
                raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode.REST_CLIENT_FAILURE, methodName, e, getBaseURL());
            }

            if (!successfulInit) {
                raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode.REST_CLIENT_FAILURE, methodName, null, getBaseURL());
            } else {

                List<AtlasEntityDef> atlasEntityDefs = atlasTypes.getEntityDefs();
                if (atlasEntityDefs != null) {
                    for (AtlasEntityDef atlasEntityDef : atlasEntityDefs) {
                        String atlasEntityTypeName = atlasEntityDef.getName();
                        atlasEntityTypesByName.put(atlasEntityTypeName, atlasEntityDef);
                    }
                }

                auditLog.logMessage(methodName, ApacheAtlasOMRSAuditCode.CONNECTED_TO_ATLAS.getMessageDefinition(getBaseURL()));

                Map<String, Object> configProperties = connectionProperties.getConfigurationProperties();
                if (configProperties != null) {
                    Object candidate = configProperties.get(ApacheAtlasOMRSRepositoryConnectorProvider.PURGE_FOR_DELETE);
                    if (candidate instanceof Boolean) {
                        purgeForDelete = (Boolean) candidate;
                    }
                }

                metadataCollection = new ApacheAtlasOMRSMetadataCollection(this,
                        serverName,
                        repositoryHelper,
                        repositoryValidator,
                        metadataCollectionId);
            }
        }

    }

    /**
     * Throws a ConnectorCheckedException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause the underlying cause of the exception (if any, null otherwise)
     * @param params any parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(ApacheAtlasOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws ConnectorCheckedException {
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

    /**
     * Throws a RepositoryErrorException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause the underlying cause of the exception (or null if none)
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
