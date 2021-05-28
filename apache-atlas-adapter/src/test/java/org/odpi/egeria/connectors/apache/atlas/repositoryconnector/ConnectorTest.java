/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector;

import org.odpi.egeria.connectors.apache.atlas.eventmapper.ApacheAtlasOMRSRepositoryEventMapper;
import org.odpi.egeria.connectors.apache.atlas.mocks.MockConnection;
import org.odpi.egeria.connectors.apache.atlas.mocks.MockConstants;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping.AttributeMapping;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.adapters.eventbus.topic.inmemory.InMemoryOpenMetadataTopicConnector;
import org.odpi.openmetadata.adapters.repositoryservices.ConnectorConfigurationFactory;
import org.odpi.openmetadata.adminservices.configuration.properties.OpenMetadataExchangeRule;
import org.odpi.openmetadata.frameworks.connectors.Connector;
import org.odpi.openmetadata.frameworks.connectors.ConnectorBroker;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectionCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.Connection;
import org.odpi.openmetadata.opentypes.OpenMetadataTypesArchive;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLog;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditLogDestination;
import org.odpi.openmetadata.repositoryservices.auditlog.OMRSAuditingComponent;
import org.odpi.openmetadata.repositoryservices.connectors.omrstopic.OMRSTopicConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.archivestore.properties.OpenMetadataArchive;
import org.odpi.openmetadata.repositoryservices.connectors.stores.archivestore.properties.OpenMetadataArchiveTypeStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.auditlogstore.OMRSAuditLogStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.eventmanagement.OMRSRepositoryEventExchangeRule;
import org.odpi.openmetadata.repositoryservices.eventmanagement.OMRSRepositoryEventManager;
import org.odpi.openmetadata.repositoryservices.eventmanagement.OMRSRepositoryEventPublisher;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentHelper;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentManager;
import org.odpi.openmetadata.repositoryservices.localrepository.repositorycontentmanager.OMRSRepositoryContentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/**
 * Test the connector(s) using the mocked server resources.
 */
public class ConnectorTest {

    private static final Logger log = LoggerFactory.getLogger(ConnectorTest.class);

    private ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector;
    private ApacheAtlasOMRSMetadataCollection atlasMetadataCollection;
    private ApacheAtlasOMRSRepositoryEventMapper atlasRepositoryEventMapper;
    private OMRSRepositoryContentManager contentManager;
    private OMRSRepositoryEventManager eventManager;
    private InMemoryOpenMetadataTopicConnector inMemoryEventConnector;
    private OMRSRepositoryHelper repositoryHelper;
    private String sourceName;

    private String metadataCollectionId;
    private String otherMetadataCollectionId;

    private List<AttributeTypeDef> supportedAttributeTypeDefs;
    private List<TypeDef> supportedTypeDefs;

    /**
     * Construct base objects.
     */
    public ConnectorTest() {

        metadataCollectionId = UUID.randomUUID().toString();
        otherMetadataCollectionId = UUID.randomUUID().toString();
        supportedAttributeTypeDefs = new ArrayList<>();
        supportedTypeDefs = new ArrayList<>();
        inMemoryEventConnector = new InMemoryOpenMetadataTopicConnector();

    }

    /**
     * Initialize the connector with some basic values.
     */
    @BeforeSuite
    public void startConnector() {

        Connection mockConnection = new MockConnection();
        ConnectorConfigurationFactory connectorConfigurationFactory = new ConnectorConfigurationFactory();
        ConnectorBroker connectorBroker = new ConnectorBroker();

        Connector auditLogConnector = null;
        try {
            auditLogConnector = connectorBroker.getConnector(connectorConfigurationFactory.getDefaultAuditLogConnection());
            auditLogConnector.start();
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            log.error("Unable to get or start audit log via the broker.", e);
            assertNull(e);
        }
        List<OMRSAuditLogStore> auditLogDestinations = new ArrayList<>();
        auditLogDestinations.add((OMRSAuditLogStore)auditLogConnector);
        OMRSAuditLogDestination destination = new OMRSAuditLogDestination("TestServer", "Test", "ODPi", auditLogDestinations);

        OMRSAuditLog auditLog = new OMRSAuditLog(destination, -1, "ConnectorTest", "Testing of the connector", null);
        contentManager = new OMRSRepositoryContentManager(MockConstants.EGERIA_USER, auditLog);
        eventManager = new OMRSRepositoryEventManager("Mock Outbound EventManager",
                new OMRSRepositoryEventExchangeRule(OpenMetadataExchangeRule.SELECTED_TYPES, Collections.emptyList()),
                new OMRSRepositoryContentValidator(contentManager),
                new OMRSAuditLog(destination, OMRSAuditingComponent.REPOSITORY_EVENT_MANAGER));

        atlasRepositoryConnector = setupConnector(
                mockConnection,
                auditLog,
                contentManager,
                eventManager,
                metadataCollectionId
        );
        atlasMetadataCollection = startConnector(atlasRepositoryConnector);
        atlasRepositoryEventMapper = setupAndStartEventMapper(auditLog, eventManager, atlasRepositoryConnector);

        repositoryHelper = atlasRepositoryConnector.getRepositoryHelper();
        sourceName = atlasRepositoryConnector.getRepositoryName();

    }

    private ApacheAtlasOMRSRepositoryConnector setupConnector(Connection connection,
                                                              OMRSAuditLog auditLog,
                                                              OMRSRepositoryContentManager contentMgr,
                                                              OMRSRepositoryEventManager eventMgr,
                                                              String mdCollectionId) {

        // TODO: setup eventManager with the InMemoryTopicConnector, so that it writes to memory rather than Kafka
        List<Connector> inMemoryConnector = new ArrayList<>();
        inMemoryConnector.add(inMemoryEventConnector);
        OMRSTopicConnector omrsTopicConnector = new OMRSTopicConnector();
        omrsTopicConnector.initializeEmbeddedConnectors(inMemoryConnector);
        OMRSRepositoryEventPublisher publisher = new OMRSRepositoryEventPublisher("Mock EventPublisher",
                omrsTopicConnector,
                auditLog.createNewAuditLog(OMRSAuditingComponent.EVENT_PUBLISHER));
        eventMgr.registerRepositoryEventProcessor(publisher);

        ConnectorBroker connectorBroker = new ConnectorBroker();

        ApacheAtlasOMRSRepositoryConnector atlasConnector = null;
        try {
            Object connector = connectorBroker.getConnector(connection);
            assertTrue(connector instanceof ApacheAtlasOMRSRepositoryConnector);
            atlasConnector = (ApacheAtlasOMRSRepositoryConnector) connector;
            atlasConnector.setAuditLog(auditLog);
            atlasConnector.setRepositoryHelper(new OMRSRepositoryContentHelper(contentMgr));
            atlasConnector.setRepositoryValidator(new OMRSRepositoryContentValidator(contentMgr));
            atlasConnector.setMetadataCollectionId(mdCollectionId);
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            log.error("Unable to get connector via the broker.", e);
            assertNull(e);
        }
        return atlasConnector;

    }

    private ApacheAtlasOMRSMetadataCollection startConnector(ApacheAtlasOMRSRepositoryConnector atlasConnector) {

        try {
            atlasConnector.start();
        } catch (ConnectorCheckedException e) {
            log.error("Unable to start connector.", e);
            assertNull(e);
        }

        ApacheAtlasOMRSMetadataCollection atlasCollection = null;
        try {
            OMRSMetadataCollection collection = atlasConnector.getMetadataCollection();
            assertTrue(collection instanceof ApacheAtlasOMRSMetadataCollection);
            atlasCollection = (ApacheAtlasOMRSMetadataCollection) collection;
            assertEquals(atlasCollection.getMetadataCollectionId(MockConstants.EGERIA_USER), atlasConnector.getMetadataCollectionId());
        } catch (RepositoryErrorException e) {
            log.error("Unable to match metadata collection IDs.", e);
            assertNotNull(e);
        }
        return atlasCollection;

    }

    private ApacheAtlasOMRSRepositoryEventMapper setupAndStartEventMapper(OMRSAuditLog auditLog,
                                                                          OMRSRepositoryEventManager eventMgr,
                                                                          ApacheAtlasOMRSRepositoryConnector atlasConnector) {

        ConnectorBroker connectorBroker = new ConnectorBroker();
        ConnectorConfigurationFactory connectorConfigurationFactory = new ConnectorConfigurationFactory();
        ApacheAtlasOMRSRepositoryEventMapper atlasEventMapper = null;
        try {
            Connection eventMapperConnection = connectorConfigurationFactory.getRepositoryEventMapperConnection(
                    "org.odpi.egeria.connectors.apache.atlas.eventmapper.ApacheAtlasOMRSRepositoryEventMapperProvider",
                    null,
                    "localhost:1080"
            );
            Object connector = connectorBroker.getConnector(eventMapperConnection);
            assertTrue(connector instanceof ApacheAtlasOMRSRepositoryEventMapper);
            atlasEventMapper = (ApacheAtlasOMRSRepositoryEventMapper) connector;
            atlasEventMapper.setAuditLog(auditLog);
            atlasEventMapper.setRepositoryEventProcessor(eventMgr);
            atlasEventMapper.initialize("Mock Apache Atlas Event Mapper", atlasConnector);
            atlasEventMapper.start();
        } catch (ConnectorCheckedException e) {
            log.info("As expected, could not fully start due to lack of Kafka.", e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to start event mapper!", e);
            assertNull(e);
        }
        return atlasEventMapper;

    }

    /**
     * Initialize all of the open metadata types that the connector could support, before running any of the actual
     * tests.
     */
    @BeforeTest
    public void initAllOpenTypes() {

        OpenMetadataArchive archive = new OpenMetadataTypesArchive().getOpenMetadataArchive();
        OpenMetadataArchiveTypeStore typeStore = archive.getArchiveTypeStore();
        List<AttributeTypeDef> attributeTypeDefList = typeStore.getAttributeTypeDefs();
        List<TypeDef> typeDefList = typeStore.getNewTypeDefs();
        for (AttributeTypeDef attributeTypeDef : attributeTypeDefList) {
            boolean supported = false;
            try {
                atlasMetadataCollection.addAttributeTypeDef(MockConstants.EGERIA_USER, attributeTypeDef);
                supported = true;
            } catch (TypeDefNotSupportedException e) {
                log.debug("AttributeTypeDef is not supported -- skipping: {}", attributeTypeDef.getName());
            } catch (RepositoryErrorException e) {
                if (e.getReportedErrorMessage().startsWith("OMRS-ATLAS-REPOSITORY-400-001")) {
                    log.debug("AttributeTypeDef is supported: {}", attributeTypeDef.getName());
                    supported = true;
                }
            } catch (InvalidParameterException | TypeDefKnownException | TypeDefConflictException | InvalidTypeDefException e) {
                log.error("Unable to process the AttributeTypeDef: {}", attributeTypeDef.getName(), e);
                assertNull(e);
            } catch (Exception e) {
                log.error("Unexpected exception trying to setup attribute type definitions.", e);
                assertNull(e);
            }
            if (supported) {
                supportedAttributeTypeDefs.add(attributeTypeDef);
            }
            contentManager.addAttributeTypeDef(atlasRepositoryConnector.getRepositoryName(), attributeTypeDef);
        }
        for (TypeDef typeDef : typeDefList) {
            boolean supported = false;
            try {
                atlasMetadataCollection.addTypeDef(MockConstants.EGERIA_USER, typeDef);
                supported = true;
            } catch (TypeDefNotSupportedException e) {
                log.debug("TypeDef is not supported -- skipping: {}", typeDef.getName());
            } catch (RepositoryErrorException e) {
                if (e.getReportedErrorMessage().startsWith("OMRS-ATLAS-REPOSITORY-400-001")) {
                    log.debug("TypeDef is supported: {}", typeDef.getName());
                    supported = true;
                }
            } catch (InvalidParameterException | TypeDefKnownException | TypeDefConflictException | InvalidTypeDefException e) {
                log.error("Unable to process the TypeDef: {}", typeDef.getName(), e);
                assertNull(e);
            } catch (Exception e) {
                log.error("Unexpected exception trying to setup type definitions.", e);
                assertNull(e);
            }
            if (supported) {
                supportedTypeDefs.add(typeDef);
            }
            contentManager.addTypeDef(atlasRepositoryConnector.getRepositoryName(), typeDef);
        }
    }

    @Test
    public void testTypeDefStore() {

        String nonExistentType = "NonExistentName";
        String classification = "Confidentiality";

        TypeDefStore typeDefStore = atlasMetadataCollection.getTypeDefStore();

        TypeDefStore.EndpointMapping endpointMapping = typeDefStore.getEndpointMappingFromAtlasName(nonExistentType, null);
        assertNull(endpointMapping);

        Map<String, Set<String>> map = typeDefStore.getAllMappedAtlasTypeDefNames(nonExistentType);
        assertTrue(map.isEmpty());
        map = typeDefStore.getAllMappedAtlasTypeDefNames(classification);
        assertNotNull(map);
        assertFalse(map.isEmpty());

        String name = typeDefStore.getMappedOMRSTypeDefName(nonExistentType, null);
        assertNull(name);
        name = typeDefStore.getMappedOMRSTypeDefName(classification, null);
        assertEquals(name, classification);

        Map<String, String> map2 = typeDefStore.getMappedOMRSTypeDefNameWithPrefixes(nonExistentType);
        assertTrue(map2.isEmpty());
        map2 = typeDefStore.getMappedOMRSTypeDefNameWithPrefixes(classification);
        assertFalse(map2.isEmpty());

        assertNull(typeDefStore.getAllTypeDefAttributesForName(nonExistentType));

    }

    @Test
    public void testInvalidConnectors() {

        Connection emptyEndpoint = new MockConnection();
        emptyEndpoint.setEndpoint(null);

        ConnectorConfigurationFactory connectorConfigurationFactory = new ConnectorConfigurationFactory();
        ConnectorBroker connectorBroker = new ConnectorBroker();

        Connector auditLogConnector = null;
        try {
            auditLogConnector = connectorBroker.getConnector(connectorConfigurationFactory.getDefaultAuditLogConnection());
            auditLogConnector.start();
        } catch (ConnectionCheckedException | ConnectorCheckedException e) {
            log.error("Unable to get or start audit log via the broker.", e);
            assertNull(e);
        }
        List<OMRSAuditLogStore> auditLogDestinations = new ArrayList<>();
        auditLogDestinations.add((OMRSAuditLogStore)auditLogConnector);
        OMRSAuditLogDestination destination = new OMRSAuditLogDestination("TestServer", "Test", "ODPi", auditLogDestinations);

        OMRSAuditLog auditLog = new OMRSAuditLog(destination, 1, "ConnectorTest2", "Testing of the connector", null);
        OMRSRepositoryContentManager invalidContent = new OMRSRepositoryContentManager(MockConstants.EGERIA_USER, auditLog);
        OMRSRepositoryEventManager invalidEvent = new OMRSRepositoryEventManager("Mock Outbound EventManager 2",
                new OMRSRepositoryEventExchangeRule(OpenMetadataExchangeRule.SELECTED_TYPES, Collections.emptyList()),
                new OMRSRepositoryContentValidator(invalidContent),
                new OMRSAuditLog(destination, OMRSAuditingComponent.REPOSITORY_EVENT_MANAGER));

        ApacheAtlasOMRSRepositoryConnector noEndpointConnector = setupConnector(
                emptyEndpoint,
                auditLog,
                invalidContent,
                invalidEvent,
                otherMetadataCollectionId
        );
        assertNotNull(noEndpointConnector);
        assertThrows(RepositoryErrorException.class, () -> noEndpointConnector.getMetadataCollection());

        Connection invalidCredentials = new MockConnection();
        invalidCredentials.setUserId("nonexistent");
        invalidCredentials.setClearPassword("nonexistent");

        ApacheAtlasOMRSRepositoryConnector invalidCredentialConnector = setupConnector(
                invalidCredentials,
                auditLog,
                invalidContent,
                invalidEvent,
                otherMetadataCollectionId
        );
        assertNotNull(invalidCredentialConnector);
        assertThrows(RepositoryErrorException.class, () -> invalidCredentialConnector.getMetadataCollection());

    }

    @Test
    public void verifySupportedTypes() {

        for (AttributeTypeDef attributeTypeDef : supportedAttributeTypeDefs) {
            try {
                assertTrue(atlasMetadataCollection.verifyAttributeTypeDef(MockConstants.EGERIA_USER, attributeTypeDef));
            } catch (InvalidParameterException | RepositoryErrorException | InvalidTypeDefException e) {
                log.error("Unable to verify attribute type definition: {}", attributeTypeDef.getName(), e);
                assertNull(e);
            } catch (Exception e) {
                log.error("Unexpected exception trying to verify attribute type definitions.", e);
                assertNull(e);
            }
        }

        for (TypeDef typeDef : supportedTypeDefs) {
            try {
                assertTrue(atlasMetadataCollection.verifyTypeDef(MockConstants.EGERIA_USER, typeDef));
            } catch (InvalidParameterException | RepositoryErrorException | InvalidTypeDefException | TypeDefNotSupportedException e) {
                log.error("Unable to verify type definition: {}", typeDef.getName(), e);
                assertNull(e);
            } catch (Exception e) {
                log.error("Unexpected exception trying to verify type definitions.", e);
                assertNull(e);
            }
        }

        try {
            TypeDefGallery typeDefGallery = atlasMetadataCollection.getAllTypes(MockConstants.EGERIA_USER);
            assertNotNull(typeDefGallery);
            List<AttributeTypeDef> fromGalleryATD = typeDefGallery.getAttributeTypeDefs();
            List<TypeDef> fromGalleryTD = typeDefGallery.getTypeDefs();
            assertTrue(fromGalleryATD.containsAll(supportedAttributeTypeDefs));
            assertTrue(supportedAttributeTypeDefs.containsAll(fromGalleryATD));
            assertTrue(fromGalleryTD.containsAll(supportedTypeDefs));
            assertTrue(supportedTypeDefs.containsAll(fromGalleryTD));
        } catch (RepositoryErrorException | InvalidParameterException e) {
            log.error("Unable to retrieve all types.", e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to retrieve all types.", e);
            assertNull(e);
        }

    }

    @Test
    public void testFindTypes() {

        List<TypeDef> entityTypeDefs = findTypeDefsByCategory(TypeDefCategory.ENTITY_DEF);
        applyAssertionsToTypeDefs(entityTypeDefs, TypeDefCategory.ENTITY_DEF);

        List<TypeDef> classificationTypeDefs = findTypeDefsByCategory(TypeDefCategory.CLASSIFICATION_DEF);
        applyAssertionsToTypeDefs(classificationTypeDefs, TypeDefCategory.CLASSIFICATION_DEF);

        List<TypeDef> relationshipTypeDefs = findTypeDefsByCategory(TypeDefCategory.RELATIONSHIP_DEF);
        applyAssertionsToTypeDefs(relationshipTypeDefs, TypeDefCategory.RELATIONSHIP_DEF);

        Map<String, Object> typeDefProperties = new HashMap<>();
        typeDefProperties.put("qualifiedName", null);
        TypeDefProperties matchProperties = new TypeDefProperties();
        matchProperties.setTypeDefProperties(typeDefProperties);

        try {
            List<TypeDef> searchResults = atlasMetadataCollection.searchForTypeDefs(MockConstants.EGERIA_USER, ".*a.*");
            assertNotNull(searchResults);
            assertFalse(searchResults.isEmpty());
            List<String> names = searchResults.stream().map(TypeDef::getName).collect(Collectors.toList());
            assertTrue(names.contains("DataSet"));
            assertTrue(names.contains("Database"));
            assertTrue(names.contains("AssetSchemaType"));
            assertTrue(names.contains("RelationalDBSchemaType"));
            assertTrue(names.contains("AttributeForSchema"));
            assertTrue(names.contains("RelationalTable"));
            List<TypeDef> typeDefsByProperty = atlasMetadataCollection.findTypeDefsByProperty(MockConstants.EGERIA_USER, matchProperties);
            assertNotNull(typeDefsByProperty);
            names = typeDefsByProperty.stream().map(TypeDef::getName).collect(Collectors.toList());
            assertTrue(names.contains("Referenceable"));
        } catch (InvalidParameterException | RepositoryErrorException e) {
            log.error("Unable to search for TypeDefs with contains string.", e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception searching for TypeDefs.", e);
            assertNull(e);
        }

    }

    private List<TypeDef> findTypeDefsByCategory(TypeDefCategory typeDefCategory) {
        List<TypeDef> typeDefs = null;
        try {
            typeDefs = atlasMetadataCollection.findTypeDefsByCategory(MockConstants.EGERIA_USER, typeDefCategory);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            log.error("Unable to search for TypeDefs from category: {}", typeDefCategory.getName(), e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for TypeDefs by category.", e);
            assertNull(e);
        }
        return typeDefs;
    }

    private void applyAssertionsToTypeDefs(List<TypeDef> typeDefs, TypeDefCategory typeDefCategory) {
        assertNotNull(typeDefs);
        assertFalse(typeDefs.isEmpty());
        for (TypeDef typeDef : typeDefs) {
            assertEquals(typeDef.getCategory(), typeDefCategory);
        }
    }

    @Test
    public void testTypeDefRetrievals() {

        final String relationalTableGUID = "ce7e72b8-396a-4013-8688-f9d973067425";
        final String relationalTableName = "RelationalTable";

        final String unknownTypeGUID = "6b60a73e-47bc-4096-9073-f94cab975958";
        final String unknownTypeName = "DesignPattern";

        try {
            TypeDef byGUID = atlasMetadataCollection.getTypeDefByGUID(MockConstants.EGERIA_USER, relationalTableGUID);
            TypeDef byName = atlasMetadataCollection.getTypeDefByName(MockConstants.EGERIA_USER, relationalTableName);
            assertNotNull(byGUID);
            assertNotNull(byName);
            assertEquals(byGUID.getName(), relationalTableName);
            assertEquals(byName.getGUID(), relationalTableGUID);
            assertEquals(byGUID, byName);
            assertThrows(TypeDefNotKnownException.class, () -> atlasMetadataCollection.getTypeDefByGUID(MockConstants.EGERIA_USER, unknownTypeGUID));
            assertThrows(TypeDefNotKnownException.class, () -> atlasMetadataCollection.getTypeDefByName(MockConstants.EGERIA_USER, unknownTypeName));
        } catch (InvalidParameterException | RepositoryErrorException | TypeDefNotKnownException e) {
            log.error("Unable to retrieve type definition: {} / {}", relationalTableGUID, relationalTableName, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying retrieve type definition.", e);
            assertNull(e);
        }

    }

    @Test
    public void testAttributeTypeDefSearches() {

        try {
            List<AttributeTypeDef> enums = atlasMetadataCollection.findAttributeTypeDefsByCategory(MockConstants.EGERIA_USER, AttributeTypeDefCategory.ENUM_DEF);
            assertNotNull(enums);
            assertEquals(enums.size(), 30);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            log.error("Unable to search for attribute type defs by ENUM category.", e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for attribute type definitions.", e);
            assertNull(e);
        }

    }

    @Test
    public void testFindTypeDefsByExternalID() {

        try {
            assertThrows(InvalidParameterException.class, () -> atlasMetadataCollection.findTypesByExternalID(MockConstants.EGERIA_USER, null, null, null));
            List<TypeDef> noTypes = atlasMetadataCollection.findTypesByExternalID(MockConstants.EGERIA_USER, "some", "org", "id");
            assertNull(noTypes);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            log.error("Unable to search for type defs by external ID.", e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for type defs by external ID.", e);
            assertNull(e);
        }

    }

    @Test
    public void testAttributeTypeDefRetrievals() {

        final String assetOwnerTypeGUID = "9548390c-69f5-4dc6-950d-6feeee257b56";
        final String assetOwnerTypeName = "AssetOwnerType";

        final String unknownTypeGUID = "123-456-789-abc-defghijklmnop";
        final String unknownTypeName = "NonExistentTypeName";

        try {
            AttributeTypeDef byGUID = atlasMetadataCollection.getAttributeTypeDefByGUID(MockConstants.EGERIA_USER, assetOwnerTypeGUID);
            AttributeTypeDef byName = atlasMetadataCollection.getAttributeTypeDefByName(MockConstants.EGERIA_USER, assetOwnerTypeName);
            assertNotNull(byGUID);
            assertNotNull(byName);
            assertEquals(byGUID.getName(), assetOwnerTypeName);
            assertEquals(byName.getGUID(), assetOwnerTypeGUID);
            assertEquals(byGUID, byName);
            assertThrows(TypeDefNotKnownException.class, () -> atlasMetadataCollection.getAttributeTypeDefByGUID(MockConstants.EGERIA_USER, unknownTypeGUID));
            assertThrows(TypeDefNotKnownException.class, () -> atlasMetadataCollection.getAttributeTypeDefByName(MockConstants.EGERIA_USER, unknownTypeName));
        } catch (InvalidParameterException | RepositoryErrorException | TypeDefNotKnownException e) {
            log.error("Unable to retrieve attribute type definition: {} / {}", assetOwnerTypeGUID, assetOwnerTypeName, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying retrieve attribute type definition.", e);
            assertNull(e);
        }

    }

    @Test
    public void testAsOfTimeMethods() {

        String ignoredEntityTypeGUID = "ce7e72b8-396a-4013-8688-f9d973067425";
        String ignoredRelationshipTypeGUID = "86b176a2-015c-44a6-8106-54d5d69ba661";
        Date now = new Date();

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.getRelationshipsForEntity(MockConstants.EGERIA_USER,
                ignoredEntityTypeGUID,
                ignoredRelationshipTypeGUID,
                0,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.findEntitiesByProperty(MockConstants.EGERIA_USER,
                ignoredEntityTypeGUID,
                null,
                null,
                0,
                null,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.findEntitiesByClassification(MockConstants.EGERIA_USER,
                ignoredEntityTypeGUID,
                "Confidentiality",
                null,
                null,
                0,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.findEntitiesByPropertyValue(MockConstants.EGERIA_USER,
                ignoredEntityTypeGUID,
                "ignore",
                0,
                null,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.findRelationshipsByProperty(MockConstants.EGERIA_USER,
                ignoredRelationshipTypeGUID,
                null,
                null,
                0,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        assertThrows(FunctionNotSupportedException.class, () -> atlasMetadataCollection.findRelationshipsByPropertyValue(MockConstants.EGERIA_USER,
                ignoredRelationshipTypeGUID,
                "ignore",
                0,
                null,
                now,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

    }

    @Test
    public void testNegativeRetrievals() {

        assertThrows(EntityNotKnownException.class, () -> atlasMetadataCollection.getEntityDetail(MockConstants.EGERIA_USER, "123"));
        assertThrows(RelationshipNotKnownException.class, () -> atlasMetadataCollection.getRelationship(MockConstants.EGERIA_USER, "123"));

    }

    @Test
    public void testSearchByProperty() {

        final String methodName = "testSearchByProperty";

        String typeGUID = "f0438d80-6eb9-4fac-bcc1-5efee5babcfc";
        String typeName = "RelationalColumnType";

        InstanceProperties ip = new InstanceProperties();

        testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10
        );

        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "encodingStandard", repositoryHelper.getExactMatchRegex("FAST_DIFF"), methodName);

        testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                1
        );

        testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.NONE,
                MockConstants.EGERIA_PAGESIZE,
                9
        );

        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "displayName", repositoryHelper.getExactMatchRegex("t"), methodName);

        testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ANY,
                MockConstants.EGERIA_PAGESIZE,
                2
        );

        ip = new InstanceProperties();
        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "qualifiedName", repositoryHelper.getExactMatchRegex("default:atlas_janus@Sandbox"), methodName);
        testFindEntitiesByProperty(
                null,
                "RelationalTable",
                ip,
                MatchCriteria.ALL,
                5,
                1
        );

        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "qualifiedName", repositoryHelper.getExactMatchRegex("default.test_hive_table1@Sandbox"), methodName);
        testFindEntitiesByProperty(
                null,
                "RelationalTable",
                ip,
                MatchCriteria.ALL,
                5,
                1
        );

    }

    @Test
    public void testSearchByPropertySorting() {

        final String methodName = "testSearchByPropertySorting";

        String typeGUID = "aa8d5470-6dbc-4648-9e2f-045e5df9d2f9";
        String typeName = "RelationalColumn";

        InstanceProperties ip = new InstanceProperties();

        List<EntityDetail> results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.GUID,
                null
        );
        EntityDetail lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getGUID().compareTo(result.getGUID()) <= 0);
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.LAST_UPDATE_OLDEST,
                null
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getUpdateTime().getTime() <= result.getUpdateTime().getTime());
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.LAST_UPDATE_RECENT,
                null
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getUpdateTime().getTime() >= result.getUpdateTime().getTime());
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.CREATION_DATE_OLDEST,
                null
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getCreateTime().getTime() <= result.getCreateTime().getTime());
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.CREATION_DATE_RECENT,
                null
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getCreateTime().getTime() >= result.getCreateTime().getTime());
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.PROPERTY_ASCENDING,
                "displayName"
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getProperties().getPropertyValue("displayName").valueAsString().compareTo(result.getProperties().getPropertyValue("displayName").valueAsString()) <= 0);
            }
        }

        results = testFindEntitiesByProperty(
                typeGUID,
                typeName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                10,
                SequencingOrder.PROPERTY_DESCENDING,
                "displayName"
        );
        lastResult = null;
        for (EntityDetail result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getProperties().getPropertyValue("displayName").valueAsString().compareTo(result.getProperties().getPropertyValue("displayName").valueAsString()) >= 0);
            }
        }

        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "name", repositoryHelper.getExactMatchRegex("default"), methodName);
        ip = repositoryHelper.addEnumPropertyToInstance(sourceName, ip, "ownerType", 99, "Other", "Another type of owner identifier, probably not supported by open metadata.", methodName);
        ip = repositoryHelper.addDatePropertyToInstance(sourceName, ip, "createTime", new Date(100000), methodName);
        testFindEntitiesByProperty(
                "0921c83f-b2db-4086-a52c-0d10e52ca078",
                "Database",
                ip,
                MatchCriteria.ANY,
                MockConstants.EGERIA_PAGESIZE,
                1,
                SequencingOrder.GUID,
                null
        );

    }

    @Test
    public void testSearchByPropertyValue() {

        String typeGUID = "f0438d80-6eb9-4fac-bcc1-5efee5babcfc";
        Set<String> possibleTypes = new HashSet<>();
        possibleTypes.add("RelationalColumnType");

        // Search by detailed type GUID first
        testFindEntitiesByPropertyValue(
                typeGUID,
                possibleTypes,
                null,
                repositoryHelper.getExactMatchRegex("atlas"),
                MockConstants.EGERIA_PAGESIZE,
                10);

        // Same search again, by supertype
        testFindEntitiesByPropertyValue(
                "a7392281-348d-48a4-bad7-f9742d7696fe",
                possibleTypes,
                null,
                repositoryHelper.getExactMatchRegex("atlas"),
                MockConstants.EGERIA_PAGESIZE,
                10);

        // Test different basic regex matching
        testFindEntitiesByPropertyValue(
                typeGUID,
                possibleTypes,
                null,
                repositoryHelper.getStartsWithRegex("atl"),
                MockConstants.EGERIA_PAGESIZE,
                10);

        testFindEntitiesByPropertyValue(
                typeGUID,
                possibleTypes,
                null,
                repositoryHelper.getContainsRegex("tla"),
                MockConstants.EGERIA_PAGESIZE,
                10);

        testFindEntitiesByPropertyValue(
                typeGUID,
                possibleTypes,
                null,
                repositoryHelper.getEndsWithRegex("las"),
                MockConstants.EGERIA_PAGESIZE,
                10);

        // Test limiting by classification
        possibleTypes = new HashSet<>();
        possibleTypes.add("RelationalColumn");
        Set<String> classifications = new HashSet<>();
        classifications.add("Confidentiality");
        List<EntityDetail> results = testFindEntitiesByPropertyValue(
                "aa8d5470-6dbc-4648-9e2f-045e5df9d2f9",
                possibleTypes,
                classifications,
                repositoryHelper.getEndsWithRegex("ation"),
                MockConstants.EGERIA_PAGESIZE,
                1);

        confirmSingleConfidentiality(results.get(0).getClassifications());

    }

    @Test
    public void testSearchByClassification() {

        final String methodName = "testSearchByClassification";

        String typeGUID = MockConstants.EXAMPLE_TYPE_GUID;
        String typeName = MockConstants.EXAMPLE_TYPE_NAME;
        String classificationName = "Confidentiality";

        InstanceProperties ip = new InstanceProperties();
        ip = repositoryHelper.addIntPropertyToInstance(sourceName, ip, "level", 3, methodName);

        List<EntityDetail> results = testFindEntitiesByClassification(
                typeGUID,
                typeName,
                classificationName,
                ip,
                MatchCriteria.ANY,
                MockConstants.EGERIA_PAGESIZE,
                1
        );
        confirmSingleConfidentiality(results.get(0).getClassifications());

        ip = repositoryHelper.addIntPropertyToInstance(sourceName, ip, "confidence", 100, methodName);
        results = testFindEntitiesByClassification(
                typeGUID,
                typeName,
                classificationName,
                ip,
                MatchCriteria.ALL,
                MockConstants.EGERIA_PAGESIZE,
                1
        );
        confirmSingleConfidentiality(results.get(0).getClassifications());

        testFindEntitiesByClassification(
                typeGUID,
                typeName,
                classificationName,
                ip,
                MatchCriteria.NONE,
                MockConstants.EGERIA_PAGESIZE,
                0
        );

        ip = repositoryHelper.addEnumPropertyToInstance(sourceName, ip, "status", 1, "Proposed", "The classification assignment was proposed by a subject matter expert.", methodName);
        ip = repositoryHelper.addStringPropertyToInstance(sourceName, ip, "notes", repositoryHelper.getContainsRegex("some notes"), methodName);
        results = testFindEntitiesByClassification(
                typeGUID,
                typeName,
                classificationName,
                ip,
                MatchCriteria.ANY,
                MockConstants.EGERIA_PAGESIZE,
                1,
                SequencingOrder.PROPERTY_ASCENDING,
                "confidence"
        );
        confirmSingleConfidentiality(results.get(0).getClassifications());

    }

    private void confirmSingleConfidentiality(List<Classification> classifications) {
        assertNotNull(classifications);
        assertEquals(classifications.size(), 1);
        Classification confidentiality = classifications.get(0);
        assertEquals(confidentiality.getType().getTypeDefName(), "Confidentiality");
        assertEquals(confidentiality.getProperties().getPropertyValue("level").valueAsString(), "3");
    }

    @Test
    public void testGetEntity() {

        Map<String, String> expectedValues = new HashMap<>();
        expectedValues.put("displayName", "location");
        expectedValues.put("qualifiedName", "default.test_hive_table1.location@Sandbox");

        EntityDetail detail = testEntityDetail(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                expectedValues);

        confirmSingleConfidentiality(detail.getClassifications());

        EntitySummary summary = testEntitySummary(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID
        );

        confirmSingleConfidentiality(summary.getClassifications());

    }

    @Test
    public void testUnknownInstances() {

        String unknownGUID = "abc123def";
        try {
            EntityDetail detail = atlasMetadataCollection.isEntityKnown(MockConstants.EGERIA_USER, unknownGUID);
            assertNull(detail);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            assertNull(e);
        }

        assertThrows(EntityNotKnownException.class, () -> atlasMetadataCollection.getRelationshipsForEntity(MockConstants.EGERIA_USER,
                unknownGUID,
                null,
                0,
                null,
                null,
                null,
                null,
                MockConstants.EGERIA_PAGESIZE));

        try {
            Relationship relationship = atlasMetadataCollection.isRelationshipKnown(MockConstants.EGERIA_USER, unknownGUID);
            assertNull(relationship);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            assertNull(e);
        }

        try {
            Relationship relationship = atlasMetadataCollection.isRelationshipKnown(MockConstants.EGERIA_USER, "HDBCTA!" + unknownGUID);
            assertNull(relationship);
        } catch (InvalidParameterException | RepositoryErrorException e) {
            assertNull(e);
        }

    }

    @Test
    public void testGetRelationshipsForEntity() {

        List<RelationshipExpectation> relationshipExpectations1 = new ArrayList<>();
        List<RelationshipExpectation> relationshipExpectations2 = new ArrayList<>();
        RelationshipExpectation one = new RelationshipExpectation(0, 1,
                "AttributeForSchema", "RelationalTableType", "RelationalColumn",
                "default.test_hive_table1@Sandbox", "default.test_hive_table1.location@Sandbox");
        RelationshipExpectation two = new RelationshipExpectation(1, 2,
                "SchemaAttributeType", "RelationalColumn", "RelationalColumnType",
                "default.test_hive_table1.location@Sandbox", "default.test_hive_table1.location@Sandbox");
        relationshipExpectations1.add(one);
        relationshipExpectations1.add(two);

        RelationshipExpectation three = new RelationshipExpectation(0, 1,
                "SchemaAttributeType", "RelationalColumn", "RelationalColumnType",
                "default.test_hive_table1.location@Sandbox", "default.test_hive_table1.location@Sandbox");
        RelationshipExpectation four = new RelationshipExpectation(1, 2,
                "AttributeForSchema", "RelationalTableType", "RelationalColumn",
                "default.test_hive_table1@Sandbox", "default.test_hive_table1.location@Sandbox");
        relationshipExpectations2.add(three);
        relationshipExpectations2.add(four);

        List<Relationship> results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations1);

        testRelationshipsAreRetrievable(results.subList(0, 1), "AttributeForSchema");
        testRelationshipsAreRetrievable(results.subList(1, 2), "SchemaAttributeType");

        results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations2,
                SequencingOrder.GUID,
                null
        );
        Relationship lastResult = null;
        for (Relationship result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getGUID().compareTo(result.getGUID()) <= 0);
            }
        }

        results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations1,
                SequencingOrder.LAST_UPDATE_OLDEST,
                null
        );
        lastResult = null;
        for (Relationship result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getUpdateTime().getTime() <= result.getUpdateTime().getTime());
            }
        }

        results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations2,
                SequencingOrder.LAST_UPDATE_RECENT,
                null
        );
        lastResult = null;
        for (Relationship result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getUpdateTime().getTime() >= result.getUpdateTime().getTime());
            }
        }

        results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations2,
                SequencingOrder.CREATION_DATE_OLDEST,
                null
        );
        lastResult = null;
        for (Relationship result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getCreateTime().getTime() <= result.getCreateTime().getTime());
            }
        }

        results = testRelationshipsForEntity(
                MockConstants.EXAMPLE_TYPE_NAME,
                MockConstants.EXAMPLE_GUID,
                2,
                relationshipExpectations1,
                SequencingOrder.CREATION_DATE_RECENT,
                null
        );
        lastResult = null;
        for (Relationship result : results) {
            if (lastResult == null) {
                lastResult = result;
            } else {
                assertTrue(lastResult.getCreateTime().getTime() >= result.getCreateTime().getTime());
            }
        }

        // TODO: property-based checks, for which we need multiple relationships that we can compare properties on

    }

    @Test
    public void testGetRelationship() {

        try {
            Relationship found = atlasMetadataCollection.isRelationshipKnown(MockConstants.EGERIA_USER, MockConstants.EXAMPLE_RELATIONSHIP_GUID);
            assertNotNull(found);
            assertEquals(found.getType().getTypeDefName(), "AttributeForSchema");
            assertEquals(found.getEntityOneProxy().getType().getTypeDefName(), "RelationalTableType");
            assertEquals(found.getEntityTwoProxy().getType().getTypeDefName(), "RelationalColumn");
        } catch (InvalidParameterException | RepositoryErrorException e) {
            log.error("Unable to find relationship directly by GUID: {}", MockConstants.EXAMPLE_RELATIONSHIP_GUID);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to find relationship directly by GUID: {}", MockConstants.EXAMPLE_RELATIONSHIP_GUID, e);
            assertNull(e);
        }

    }

    @AfterSuite
    public void stopConnector() {
        try {
            atlasRepositoryEventMapper.disconnect();
            atlasRepositoryConnector.disconnect();
        } catch (ConnectorCheckedException e) {
            log.error("Unable to properly disconnect connector.", e);
        }
    }

    /**
     * Executes a common set of tests against a list of EntityDetail objects after first searching for them by property
     * value.
     *
     * @param typeGUID the entity type GUID to search
     * @param possibleTypes the names of the types that could be returned by the search
     * @param classificationLimiters the names of classifications by which to limit the results (or null if not to limit)
     * @param queryString the string criteria by which to search
     * @param pageSize to limit the results
     * @param totalNumberExpected the total number of expected results
     * @return {@code List<EntityDetail>} the results of the query
     */
    private List<EntityDetail> testFindEntitiesByPropertyValue(String typeGUID,
                                                               Set<String> possibleTypes,
                                                               Set<String> classificationLimiters,
                                                               String queryString,
                                                               int pageSize,
                                                               int totalNumberExpected) {

        List<EntityDetail> results = null;
        List<String> classifications = null;
        if (classificationLimiters != null) {
            classifications = new ArrayList<>(classificationLimiters);
        }

        try {
            results = atlasMetadataCollection.findEntitiesByPropertyValue(
                    MockConstants.EGERIA_USER,
                    typeGUID,
                    queryString,
                    0,
                    null,
                    classifications,
                    null,
                    null,
                    null,
                    pageSize
            );
        } catch (InvalidParameterException | TypeErrorException | RepositoryErrorException | PropertyErrorException | PagingErrorException | FunctionNotSupportedException | UserNotAuthorizedException e) {
            log.error("Unable to search for entities of type '{}' by property value.", typeGUID, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for entities of type '{}' by property value.", typeGUID, e);
            assertNull(e);
        }

        if (totalNumberExpected <= 0) {
            assertTrue(results == null || results.isEmpty());
        } else {
            assertNotNull(results);
            assertFalse(results.isEmpty());
            assertEquals(results.size(), totalNumberExpected);
            for (EntityDetail result : results) {
                assertTrue(possibleTypes.contains(result.getType().getTypeDefName()));
                assertTrue(result.getVersion() >= 0);
            }
        }

        return results;

    }

    private List<EntityDetail> testFindEntitiesByProperty(String typeGUID,
                                                          String typeName,
                                                          InstanceProperties matchProperties,
                                                          MatchCriteria matchCriteria,
                                                          int pageSize,
                                                          int totalNumberExpected) {
        return testFindEntitiesByProperty(
                typeGUID,
                typeName,
                matchProperties,
                matchCriteria,
                pageSize,
                totalNumberExpected,
                null,
                null);
    }

    /**
     * Executes a common set of tests against a list of EntityDetail objects after first searching for them by property.
     *
     * @param typeGUID the entity type GUID to search
     * @param typeName the name of the type to search
     * @param matchProperties the properties to match against
     * @param matchCriteria the criteria by which to match
     * @param pageSize to limit the results
     * @param totalNumberExpected the total number of expected results
     * @return {@code List<EntityDetail>} the results of the query
     */
    private List<EntityDetail> testFindEntitiesByProperty(String typeGUID,
                                                          String typeName,
                                                          InstanceProperties matchProperties,
                                                          MatchCriteria matchCriteria,
                                                          int pageSize,
                                                          int totalNumberExpected,
                                                          SequencingOrder sequencingOrder,
                                                          String sequencingProperty) {

        List<EntityDetail> results = null;

        try {
            results = atlasMetadataCollection.findEntitiesByProperty(
                    MockConstants.EGERIA_USER,
                    typeGUID,
                    matchProperties,
                    matchCriteria,
                    0,
                    null,
                    null,
                    null,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize
            );
        } catch (InvalidParameterException | TypeErrorException | RepositoryErrorException | PropertyErrorException | PagingErrorException | FunctionNotSupportedException | UserNotAuthorizedException e) {
            log.error("Unable to search for {} entities by property: {}", typeName, matchProperties, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for {} entities by property: {}", typeName, matchProperties, e);
            assertNull(e);
        }

        if (totalNumberExpected <= 0) {
            assertTrue(results == null || results.isEmpty());
        } else {
            assertNotNull(results);
            assertFalse(results.isEmpty());
            assertEquals(results.size(), totalNumberExpected);
            for (EntityDetail result : results) {
                assertEquals(result.getType().getTypeDefName(), typeName);
                assertTrue(result.getVersion() >= 0);

                // TODO: need to understand how the refresh request methods are actually meant to work...
                /*
                try {
                    atlasMetadataCollection.refreshEntityReferenceCopy(MockConstants.EGERIA_USER,
                            result.getGUID(),
                            result.getType().getTypeDefGUID(),
                            result.getType().getTypeDefName(),
                            result.getMetadataCollectionId());
                } catch (InvalidParameterException | RepositoryErrorException | HomeEntityException | UserNotAuthorizedException e) {
                    log.error("Unable to send a refresh event for entity GUID: {}", result.getGUID());
                    assertNull(e);
                } catch (Exception e) {
                    log.error("Unexpected exception trying to send a refresh event for entity GUID: {}", result.getGUID(), e);
                    assertNull(e);
                }*/

            }
        }

        return results;

    }

    private List<EntityDetail> testFindEntitiesByClassification(String typeGUID,
                                                                String typeName,
                                                                String classificationName,
                                                                InstanceProperties matchClassificationProperties,
                                                                MatchCriteria matchCriteria,
                                                                int pageSize,
                                                                int totalNumberExpected) {
        return testFindEntitiesByClassification(
                typeGUID,
                typeName,
                classificationName,
                matchClassificationProperties,
                matchCriteria,
                pageSize,
                totalNumberExpected,
                null,
                null);
    }

    /**
     * Executes a common set of tests against a list of EntityDetail objects after first searching for them by
     * classification property.
     *
     * @param typeGUID the entity type GUID to search
     * @param typeName the name of the type to search
     * @param classificationName the name of the classification by which to limit the results
     * @param matchClassificationProperties the properties of the classification to match against
     * @param matchCriteria the criteria by which to match
     * @param pageSize to limit the results
     * @param totalNumberExpected the total number of expected results
     * @return {@code List<EntityDetail>} the results of the query
     */
    private List<EntityDetail> testFindEntitiesByClassification(String typeGUID,
                                                                String typeName,
                                                                String classificationName,
                                                                InstanceProperties matchClassificationProperties,
                                                                MatchCriteria matchCriteria,
                                                                int pageSize,
                                                                int totalNumberExpected,
                                                                SequencingOrder sequencingOrder,
                                                                String sequencingProperty) {

        List<EntityDetail> results = null;

        try {
            results = atlasMetadataCollection.findEntitiesByClassification(
                    MockConstants.EGERIA_USER,
                    typeGUID,
                    classificationName,
                    matchClassificationProperties,
                    matchCriteria,
                    0,
                    null,
                    null,
                    sequencingProperty,
                    sequencingOrder,
                    pageSize
            );
        } catch (InvalidParameterException | TypeErrorException | RepositoryErrorException | PropertyErrorException | PagingErrorException | FunctionNotSupportedException | UserNotAuthorizedException e) {
            log.error("Unable to search for {} entities by classification {} with properties: {}", typeName, classificationName, matchClassificationProperties, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception trying to search for {} entities by classification {} with properties: {}", typeName, classificationName, matchClassificationProperties, e);
            assertNull(e);
        }

        if (totalNumberExpected <= 0) {
            assertTrue(results == null || results.isEmpty());
        } else {
            assertNotNull(results);
            assertFalse(results.isEmpty());
            assertEquals(results.size(), totalNumberExpected);
            for (EntityDetail result : results) {
                assertEquals(result.getType().getTypeDefName(), typeName);
                assertTrue(result.getVersion() >= 0);
            }
        }

        return results;

    }

    /**
     * Executes a common set of tests against an EntityDetail object after first directly retrieving it.
     *
     * @param omrsType the type of OMRS object
     * @param guid the GUID of the object
     * @param expectedValues a map of any expected values that will be asserted for equality (property name to value)
     * @return EntityDetail that is retrieved
     */
    private EntityDetail testEntityDetail(String omrsType, String guid, Map<String, String> expectedValues) {

        EntityDetail detail = null;

        try {
            detail = atlasMetadataCollection.isEntityKnown(MockConstants.EGERIA_USER, guid);
        } catch (RepositoryErrorException | InvalidParameterException e) {
            log.error("Unable to retrieve entity detail for {}.", omrsType, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception retrieving {} detail.", omrsType, e);
            assertNull(e);
        }

        assertNotNull(detail);
        assertEquals(detail.getType().getTypeDefName(), omrsType);
        assertTrue(detail.getVersion() >= 0);
        assertNotNull(detail.getMetadataCollectionId());

        testExpectedValuesForEquality(detail.getProperties(), expectedValues);

        return detail;

    }

    /**
     * Executes a common set of tests against an EntitySummary object after first directly retrieving it.
     *
     * @param omrsType the type of OMRS object
     * @param guid the GUID of the object
     * @return EntitySummary that is retrieved
     */
    private EntitySummary testEntitySummary(String omrsType, String guid) {

        EntitySummary summary = null;

        try {
            summary = atlasMetadataCollection.getEntitySummary(MockConstants.EGERIA_USER, guid);
        } catch (RepositoryErrorException | InvalidParameterException | EntityNotKnownException e) {
            log.error("Unable to retrieve entity detail for {}.", omrsType, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception retrieving {} detail.", omrsType, e);
            assertNull(e);
        }

        assertNotNull(summary);
        assertEquals(summary.getType().getTypeDefName(), omrsType);
        assertTrue(summary.getVersion() >= 0);
        assertNotNull(summary.getMetadataCollectionId());

        return summary;

    }

    /**
     * Tests whether the provided properties have values equal to those that are expected.
     *
     * @param properties the properties to check
     * @param expectedValues the expected values of the properties (property name to value)
     */
    private void testExpectedValuesForEquality(InstanceProperties properties, Map<String, String> expectedValues) {
        if (expectedValues != null) {
            for (Map.Entry<String, String> expected : expectedValues.entrySet()) {
                String propertyName = expected.getKey();
                String expectedValue = expected.getValue();
                assertTrue(AttributeMapping.valuesMatch(properties.getPropertyValue(propertyName), expectedValue));
            }
        }
    }

    private List<Relationship> testRelationshipsForEntity(String omrsType,
                                                          String guid,
                                                          int totalNumberExpected,
                                                          List<RelationshipExpectation> relationshipExpectations) {
        return testRelationshipsForEntity(
                omrsType,
                guid,
                totalNumberExpected,
                relationshipExpectations,
                null,
                null);
    }

    /**
     * Executes a common set of tests against a list of Relationship objects after first directly retrieving them.
     *
     * @param omrsType the type of OMRS object
     * @param guid the GUID of the relationship object
     * @param totalNumberExpected the total number of relationships expected
     * @param relationshipExpectations a list of relationship expectations
     * @return {@code List<Relationship>} the list of relationships retrieved
     */
    private List<Relationship> testRelationshipsForEntity(String omrsType,
                                                          String guid,
                                                          int totalNumberExpected,
                                                          List<RelationshipExpectation> relationshipExpectations,
                                                          SequencingOrder sequencingOrder,
                                                          String sequencingProperty) {

        List<Relationship> relationships = null;

        try {
            relationships = atlasMetadataCollection.getRelationshipsForEntity(
                    MockConstants.EGERIA_USER,
                    guid,
                    null,
                    0,
                    null,
                    null,
                    sequencingProperty,
                    sequencingOrder,
                    MockConstants.EGERIA_PAGESIZE
            );
        } catch (InvalidParameterException | TypeErrorException | RepositoryErrorException | EntityNotKnownException | PagingErrorException | FunctionNotSupportedException | UserNotAuthorizedException e) {
            log.error("Unable to retrieve relationships for {}.", omrsType, e);
            assertNull(e);
        } catch (Exception e) {
            log.error("Unexpected exception retrieving {} relationships.", omrsType, e);
            assertNull(e);
        }

        if (totalNumberExpected <= 0) {
            assertTrue(relationships == null || relationships.isEmpty());
        } else {
            assertNotNull(relationships);
            assertFalse(relationships.isEmpty());
            assertEquals(relationships.size(), totalNumberExpected);
            for (RelationshipExpectation relationshipExpectation : relationshipExpectations) {
                for (int i = relationshipExpectation.getStartIndex(); i < relationshipExpectation.getFinishIndex(); i++) {

                    Relationship candidate = relationships.get(i);
                    assertEquals(candidate.getType().getTypeDefName(), relationshipExpectation.getOmrsType());
                    EntityProxy one = candidate.getEntityOneProxy();
                    EntityProxy two = candidate.getEntityTwoProxy();
                    assertTrue(relationshipExpectation.getProxyOneTypes().contains(one.getType().getTypeDefName()));
                    assertTrue(one.getVersion() >= 0);
                    testQualifiedNameEquality(relationshipExpectation.getExpectedProxyOneQN(), one.getUniqueProperties().getPropertyValue("qualifiedName"));
                    assertTrue(relationshipExpectation.getProxyTwoTypes().contains(two.getType().getTypeDefName()));
                    assertTrue(two.getVersion() >= 0);
                    testQualifiedNameEquality(relationshipExpectation.getExpectedProxyTwoQN(), two.getUniqueProperties().getPropertyValue("qualifiedName"));

                    // TODO: need to understand how the refresh request methods are actually meant to work...
                    /*
                    try {
                        atlasMetadataCollection.refreshRelationshipReferenceCopy(MockConstants.EGERIA_USER,
                                candidate.getGUID(),
                                candidate.getType().getTypeDefGUID(),
                                candidate.getType().getTypeDefName(),
                                otherMetadataCollectionId);
                    } catch (InvalidParameterException | RepositoryErrorException | HomeRelationshipException | UserNotAuthorizedException e) {
                        log.error("Unable to send a refresh event for relationship GUID: {}", candidate.getGUID());
                        assertNull(e);
                    } catch (Exception e) {
                        log.error("Unexpected exception trying to send a refresh event for relationship GUID: {}", candidate.getGUID(), e);
                        assertNull(e);
                    }*/

                }

            }
        }

        return relationships;

    }

    /**
     * Attempt to re-retrieve the provided relationships by their GUID.
     *
     * @param relationships list of relationships to test re-retrieval
     * @param typeName the expected type of the relationship
     */
    private void testRelationshipsAreRetrievable(List<Relationship> relationships, String typeName) {

        for (Relationship result : relationships) {
            assertEquals(result.getType().getTypeDefName(), typeName);
            assertTrue(result.getVersion() >= 0);

            try {
                Relationship foundAgain = atlasMetadataCollection.isRelationshipKnown(MockConstants.EGERIA_USER, result.getGUID());
                assertNotNull(foundAgain);
                assertEquals(foundAgain, result);
            } catch (InvalidParameterException | RepositoryErrorException e) {
                log.error("Unable to find relationship again by GUID: {}", result.getGUID());
                assertNull(e);
            } catch (Exception e) {
                log.error("Unexpected exception trying to find relationship again by GUID: {}", result.getGUID(), e);
                assertNull(e);
            }

        }

    }

    /**
     * Test whether the provided qualifiedNames are equal or not.
     * @param expectedQN the expected qualifiedName
     * @param foundValue the found qualifiedName
     */
    private void testQualifiedNameEquality(String expectedQN, InstancePropertyValue foundValue) {
        if (expectedQN != null) {
            assertTrue(AttributeMapping.valuesMatch(foundValue, expectedQN));
        }
    }

    /**
     * Utility class for defining the expectations to check against a set of relationships.
     */
    protected class RelationshipExpectation {

        private int startIndex;
        private int finishIndex;
        private String omrsType;
        private Set<String> proxyOneTypes;
        private Set<String> proxyTwoTypes;
        private String expectedProxyOneQN;
        private String expectedProxyTwoQN;

        private RelationshipExpectation() {
            proxyOneTypes = new HashSet<>();
            proxyTwoTypes = new HashSet<>();
        }

        RelationshipExpectation(int startIndex, int finishIndex, String omrsType, String proxyOneType, String proxyTwoType) {
            this();
            this.startIndex = startIndex;
            this.finishIndex = finishIndex;
            this.omrsType = omrsType;
            this.proxyOneTypes.add(proxyOneType);
            this.proxyTwoTypes.add(proxyTwoType);
        }

        RelationshipExpectation(int startIndex,
                                int finishIndex,
                                String omrsType,
                                String proxyOneType,
                                String proxyTwoType,
                                String expectedProxyOneQN,
                                String expectedProxyTwoQN) {
            this(startIndex, finishIndex, omrsType, proxyOneType, proxyTwoType);
            this.expectedProxyOneQN = expectedProxyOneQN;
            this.expectedProxyTwoQN = expectedProxyTwoQN;
        }

        RelationshipExpectation(int startIndex,
                                int finishIndex,
                                String omrsType,
                                Set<String> proxyOneTypes,
                                Set<String> proxyTwoTypes,
                                String expectedProxyOneQN,
                                String expectedProxyTwoQN) {
            this.startIndex = startIndex;
            this.finishIndex = finishIndex;
            this.omrsType = omrsType;
            this.proxyOneTypes = proxyOneTypes;
            this.proxyTwoTypes = proxyTwoTypes;
            this.expectedProxyOneQN = expectedProxyOneQN;
            this.expectedProxyTwoQN = expectedProxyTwoQN;
        }

        int getStartIndex() { return startIndex; }
        int getFinishIndex() { return finishIndex; }
        String getOmrsType() { return omrsType; }
        Set<String> getProxyOneTypes() { return proxyOneTypes; }
        Set<String> getProxyTwoTypes() { return proxyTwoTypes; }
        String getExpectedProxyOneQN() { return expectedProxyOneQN; }
        String getExpectedProxyTwoQN() { return expectedProxyTwoQN; }

    }

}
