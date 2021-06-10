/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector;

import org.odpi.openmetadata.frameworks.connectors.properties.beans.ConnectorType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnectorProviderBase;

import java.util.ArrayList;
import java.util.List;

/**
 * In the Open Connector Framework (OCF), a ConnectorProvider is a factory for a specific type of connector.
 * The ApacheAtlasOMRSRepositoryConnectorProvider is the connector provider for the ApacheAtlasOMRSRepositoryConnector.
 * It extends OMRSRepositoryConnectorProviderBase which in turn extends the OCF ConnectorProviderBase.
 * ConnectorProviderBase supports the creation of connector instances.
 * <p>
 * The ApacheAtlasOMRSRepositoryConnectorProvider must initialize ConnectorProviderBase with the Java class
 * name of the OMRS Connector implementation (by calling super.setConnectorClassName(className)).
 * Then the connector provider will work.
 */
public class ApacheAtlasOMRSRepositoryConnectorProvider extends OMRSRepositoryConnectorProviderBase {

    static final String CONNECTOR_TYPE_GUID = "7b200ca2-655b-4106-917b-abddf2ec3aa4";
    static final String CONNECTOR_TYPE_NAME = "OMRS Apache Atlas Repository Connector";
    static final String CONNECTOR_TYPE_DESC = "OMRS Apache Atlas Repository Connector that processes events from the Apache Atlas repository store.";

    public static final String PURGE_FOR_DELETE = "purgeForDelete";

    /**
     * Constructor used to initialize the ConnectorProviderBase with the Java class name of the specific
     * OMRS Connector implementation.
     */
    public ApacheAtlasOMRSRepositoryConnectorProvider() {

        Class<?> connectorClass = ApacheAtlasOMRSRepositoryConnector.class;
        super.setConnectorClassName(connectorClass.getName());

        ConnectorType connectorType = new ConnectorType();
        connectorType.setType(ConnectorType.getConnectorTypeType());
        connectorType.setGUID(CONNECTOR_TYPE_GUID);
        connectorType.setQualifiedName(CONNECTOR_TYPE_NAME);
        connectorType.setDisplayName(CONNECTOR_TYPE_NAME);
        connectorType.setDescription(CONNECTOR_TYPE_DESC);
        connectorType.setConnectorProviderClassName(this.getClass().getName());

        List<String> knownConfigProperties = new ArrayList<>();
        knownConfigProperties.add(PURGE_FOR_DELETE);
        connectorType.setRecognizedConfigurationProperties(knownConfigProperties);

        super.connectorTypeBean = connectorType;

    }
}
