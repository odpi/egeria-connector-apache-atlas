/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.typedef.AtlasClassificationDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSRepositoryConnector;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.AttributeTypeDefStore;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.PatchErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that generically handles converting between Apache Atlas and OMRS Classification TypeDefs.
 */
public abstract class ClassificationDefMapping extends BaseTypeDefMapping {

    private static final Logger log = LoggerFactory.getLogger(ClassificationDefMapping.class);

    private ClassificationDefMapping() {
        // Do nothing...
    }

    /**
     * Adds the provided OMRS type definition to Apache Atlas (if possible), or throws a TypeDefNotSupportedException
     * if not possible.
     *
     * @param omrsClassificationDef the OMRS ClassificationDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented AttributeTypeDefs in Apache Atlas
     * @param atlasRepositoryConnector connectivity to the Apache Atlas environment
     * @throws TypeDefNotSupportedException when classification cannot be fully represented in Atlas
     */
    public static void addClassificationTypeToAtlas(ClassificationDef omrsClassificationDef,
                                                    TypeDefStore typeDefStore,
                                                    AttributeTypeDefStore attributeDefStore,
                                                    ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector) throws TypeDefNotSupportedException {

        final String methodName = "addClassificationTypeToAtlas";

        String omrsTypeDefName = omrsClassificationDef.getName();
        AtlasTypesDef atlasTypesDef = setupClassificationType(omrsClassificationDef, typeDefStore, attributeDefStore);

        if (atlasTypesDef != null) {
            try {
                atlasRepositoryConnector.createTypeDef(atlasTypesDef);
                typeDefStore.addTypeDef(omrsClassificationDef);
            } catch (AtlasServiceException e) {
                typeDefStore.addUnimplementedTypeDef(omrsClassificationDef);
                raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, e, omrsTypeDefName, atlasRepositoryConnector.getServerName());
            }
        } else {
            // Otherwise, we'll drop it as unimplemented
            typeDefStore.addUnimplementedTypeDef(omrsClassificationDef);
            raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, atlasRepositoryConnector.getServerName());
        }

    }

    /**
     * Updates the provided OMRS type definition in Apache Atlas (if possible), or throws a PatchErrorException
     * if not possible.
     *
     * @param omrsClassificationDef the OMRS ClassificationDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented AttributeTypeDefs in Apache Atlas
     * @param atlasRepositoryConnector connectivity to the Apache Atlas environment
     * @throws PatchErrorException if the patched typedef cannot be fully represented in Atlas
     */
    public static void updateClassificationTypeInAtlas(ClassificationDef omrsClassificationDef,
                                                       TypeDefStore typeDefStore,
                                                       AttributeTypeDefStore attributeDefStore,
                                                       ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector) throws PatchErrorException {

        final String methodName = "updateClassificationTypeInAtlas";

        String omrsTypeDefName = omrsClassificationDef.getName();
        AtlasTypesDef atlasTypesDef = setupClassificationType(omrsClassificationDef, typeDefStore, attributeDefStore);

        if (atlasTypesDef != null) {
            try {
                atlasRepositoryConnector.updateTypeDef(atlasTypesDef);
                typeDefStore.addTypeDef(omrsClassificationDef);
            } catch (AtlasServiceException e) {
                typeDefStore.addUnimplementedTypeDef(omrsClassificationDef);
                raisePatchErrorException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, e, omrsTypeDefName, atlasRepositoryConnector.getServerName());
            }
        } else {
            // Otherwise, we'll drop it as unimplemented
            typeDefStore.addUnimplementedTypeDef(omrsClassificationDef);
            raisePatchErrorException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, atlasRepositoryConnector.getServerName());
        }

    }

    /**
     * Setup the provided OMRS type definition as an Apache Atlas type definition (if possible), or return null if
     * not possible.
     *
     * @param omrsClassificationDef the OMRS ClassificationDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented AttributeTypeDefs in Apache Atlas
     */
    private static AtlasTypesDef setupClassificationType(ClassificationDef omrsClassificationDef,
                                                         TypeDefStore typeDefStore,
                                                         AttributeTypeDefStore attributeDefStore) {

        String omrsTypeDefName = omrsClassificationDef.getName();
        boolean fullyCovered = true;

        // Map base properties
        AtlasClassificationDef classificationTypeDef = new AtlasClassificationDef();
        setupBaseMapping(omrsClassificationDef, classificationTypeDef);

        // Map classification-specific properties
        Set<String> entitiesForAtlas = new HashSet<>();
        List<TypeDefLink> validEntities = omrsClassificationDef.getValidEntityDefs();
        for (TypeDefLink typeDefLink : validEntities) {
            String omrsEntityName = typeDefLink.getName();
            // TODO: assumes all classifications remain one-to-one (never generated, so never a prefix)
            Set<String> atlasEntityName = typeDefStore.getMappedAtlasTypeDefNames(omrsEntityName, null);
            if (atlasEntityName != null) {
                entitiesForAtlas.addAll(atlasEntityName);
            }
        }
        if (entitiesForAtlas.isEmpty()) {
            log.warn("No relevant Atlas entities found for classification: {}", omrsTypeDefName);
            fullyCovered = false;
        }
        classificationTypeDef.setEntityTypes(entitiesForAtlas);

        fullyCovered = fullyCovered && setupPropertyMappings(omrsClassificationDef, classificationTypeDef, attributeDefStore);

        AtlasTypesDef atlasTypesDef = null;
        if (fullyCovered) {
            // Only create the classification if we can fully model it
            atlasTypesDef = new AtlasTypesDef();
            List<AtlasClassificationDef> classificationList = new ArrayList<>();
            classificationList.add(classificationTypeDef);
            atlasTypesDef.setClassificationDefs(classificationList);
        }
        return atlasTypesDef;

    }

}
