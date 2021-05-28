/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.mapping;

import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.typedef.AtlasRelationshipDef;
import org.apache.atlas.model.typedef.AtlasRelationshipEndDef;
import org.apache.atlas.model.typedef.AtlasStructDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.odpi.egeria.connectors.apache.atlas.auditlog.ApacheAtlasOMRSErrorCode;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.ApacheAtlasOMRSRepositoryConnector;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.AttributeTypeDefStore;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores.TypeDefStore;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.RelationshipDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.RelationshipEndCardinality;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.RelationshipEndDef;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.PatchErrorException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.TypeDefNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class that generically handles converting between Apache Atlas and OMRS Relationship TypeDefs.
 */
public abstract class RelationshipDefMapping extends BaseTypeDefMapping {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDefMapping.class);

    private RelationshipDefMapping() {
        // Do nothing...
    }

    /**
     * Adds the provided OMRS type definition to Apache Atlas (if possible), or throws a TypeDefNotSupportedException
     * if not possible.
     *
     * @param omrsRelationshipDef the OMRS RelationshipDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented TypeDefAttributes in Apache Atlas
     * @param atlasRepositoryConnector connectivity to the Apache Atlas environment
     * @throws TypeDefNotSupportedException when the typedef cannot be fully represented in Atlas
     */
    public static void addRelationshipTypeToAtlas(RelationshipDef omrsRelationshipDef,
                                                  TypeDefStore typeDefStore,
                                                  AttributeTypeDefStore attributeDefStore,
                                                  ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector) throws TypeDefNotSupportedException {

        final String methodName = "addRelationshipTypeToAtlas";

        String omrsTypeDefName = omrsRelationshipDef.getName();
        AtlasTypesDef atlasTypesDef = setupRelationshipType(omrsRelationshipDef, typeDefStore, attributeDefStore);

        if (atlasTypesDef != null) {
            // Only create the relationship if we can fully model it
            try {
                atlasRepositoryConnector.createTypeDef(atlasTypesDef);
                typeDefStore.addTypeDef(omrsRelationshipDef);
            } catch (AtlasServiceException e) {
                typeDefStore.addUnimplementedTypeDef(omrsRelationshipDef);
                raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, e, omrsTypeDefName, atlasRepositoryConnector.getServerName());
            }
        } else {
            // Otherwise, we'll drop it as unimplemented
            typeDefStore.addUnimplementedTypeDef(omrsRelationshipDef);
            raiseTypeDefNotSupportedException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, atlasRepositoryConnector.getServerName());
        }

    }

    /**
     * Updates the provided OMRS type definition in Apache Atlas (if possible), or throws a PatchErrorException
     * if not possible.
     *
     * @param omrsRelationshipDef the OMRS RelationshipDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented TypeDefAttributes in Apache Atlas
     * @param atlasRepositoryConnector connectivity to the Apache Atlas environment
     * @throws PatchErrorException if the patched typedef cannot be fully represented in Atlas
     */
    public static void updateRelationshipTypeInAtlas(RelationshipDef omrsRelationshipDef,
                                                     TypeDefStore typeDefStore,
                                                     AttributeTypeDefStore attributeDefStore,
                                                     ApacheAtlasOMRSRepositoryConnector atlasRepositoryConnector) throws PatchErrorException {

        final String methodName = "updateRelationshipTypeInAtlas";

        String omrsTypeDefName = omrsRelationshipDef.getName();
        AtlasTypesDef atlasTypesDef = setupRelationshipType(omrsRelationshipDef, typeDefStore, attributeDefStore);

        if (atlasTypesDef != null) {
            // Only create the relationship if we can fully model it
            try {
                atlasRepositoryConnector.updateTypeDef(atlasTypesDef);
            } catch (AtlasServiceException e) {
                typeDefStore.addUnimplementedTypeDef(omrsRelationshipDef);
                raisePatchErrorException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, e, omrsTypeDefName, atlasRepositoryConnector.getServerName());
            }
        } else {
            // Otherwise, we'll drop it as unimplemented
            typeDefStore.addUnimplementedTypeDef(omrsRelationshipDef);
            raisePatchErrorException(ApacheAtlasOMRSErrorCode.TYPEDEF_NOT_SUPPORTED, methodName, null, omrsTypeDefName, atlasRepositoryConnector.getServerName());
        }

    }

    /**
     * Setup the provided OMRS type definition as an Apache Atlas type definition (if possible), or return null if
     * not possible.
     *
     * @param omrsRelationshipDef the OMRS RelationshipDef to add to Apache Atlas
     * @param typeDefStore the store of mapped / implemented TypeDefs in Apache Atlas
     * @param attributeDefStore the store of mapped / implemented TypeDefAttributes in Apache Atlas
     */
    private static AtlasTypesDef setupRelationshipType(RelationshipDef omrsRelationshipDef,
                                                       TypeDefStore typeDefStore,
                                                       AttributeTypeDefStore attributeDefStore) {

        // Map base properties
        AtlasRelationshipDef relationshipTypeDef = new AtlasRelationshipDef();
        setupBaseMapping(omrsRelationshipDef, relationshipTypeDef);

        // Map entity-specific properties:
        // a) endpoints
        boolean fullyCovered = setupRelationshipEnds(omrsRelationshipDef, relationshipTypeDef, typeDefStore);

        // b) propagation
        if (fullyCovered) {
            switch (omrsRelationshipDef.getPropagationRule()) {
                case NONE:
                    relationshipTypeDef.setPropagateTags(AtlasRelationshipDef.PropagateTags.NONE);
                    break;
                case BOTH:
                    relationshipTypeDef.setPropagateTags(AtlasRelationshipDef.PropagateTags.BOTH);
                    break;
                case ONE_TO_TWO:
                    relationshipTypeDef.setPropagateTags(AtlasRelationshipDef.PropagateTags.ONE_TO_TWO);
                    break;
                case TWO_TO_ONE:
                    relationshipTypeDef.setPropagateTags(AtlasRelationshipDef.PropagateTags.TWO_TO_ONE);
                    break;
            }
        }

        // c) relationship category
        if (fullyCovered) {
            setupCardinality(relationshipTypeDef);
        }

        // TODO: relationship label seems optional (not all out-of-the-box relationships have them), not sure of impact?

        fullyCovered = fullyCovered && setupPropertyMappings(omrsRelationshipDef, relationshipTypeDef, attributeDefStore);

        AtlasTypesDef atlasTypesDef = null;
        if (fullyCovered) {
            // Only create the relationship if we can fully model it
            atlasTypesDef = new AtlasTypesDef();
            List<AtlasRelationshipDef> relationshipList = new ArrayList<>();
            relationshipList.add(relationshipTypeDef);
            atlasTypesDef.setRelationshipDefs(relationshipList);
        }
        return atlasTypesDef;

    }

    /**
     * Sets up the relationship ends of the Apache Atlas relationship type definition.
     *
     * @param omrsRelationshipDef the OMRS RelationshipDef to add to Apache Atlas
     * @param atlasRelationshipDef the Apache Atlas RelationshipDef
     * @param typeDefStore the store of TypeDef mappings
     * @return boolean indicating full coverage (true) or not (false) of the relationship ends
     */
    private static boolean setupRelationshipEnds(RelationshipDef omrsRelationshipDef,
                                                 AtlasRelationshipDef atlasRelationshipDef,
                                                 TypeDefStore typeDefStore) {

        // Note that the way endpoints are specified between OMRS and Apache Atlas differs:
        // - Apache Atlas lists the attribute name as seen on the endpoint's type itself,
        //   with cardinality being the cardinality of that attribute
        // - OMRS lists the attribute name as the endpoint's type is referred to by the other
        //   end of the relationship, with cardinality also being from the perspective of that
        //   other endpoint
        // ... meaning we need to invert the attribute names and cardinalities as part of the
        //     translation (and we'll need to do the same within the mapping we store)

        RelationshipEndDef omrs1 = omrsRelationshipDef.getEndDef1();
        RelationshipEndDef omrs2 = omrsRelationshipDef.getEndDef2();
        AtlasRelationshipEndDef atlas1 = new AtlasRelationshipEndDef();
        AtlasRelationshipEndDef atlas2 = new AtlasRelationshipEndDef();

        String omrsTypeName1 = omrs1.getEntityType().getName();
        String omrsTypeName2 = omrs2.getEntityType().getName();

        // TODO: at the moment this method is only used when adding new relationship types to Atlas,
        //  so this assumes that we will not use generated objects / prefixes for these net-new relationship types
        Set<String> atlasTypeNames1 = typeDefStore.getMappedAtlasTypeDefNames(omrsTypeName1, null);
        if (atlasTypeNames1.size() > 1) {
            log.warn("Found multiple mapped types, when expected only one: {}", omrsTypeName1);
        }
        String atlasTypeName1 = null;
        for (String candidate : atlasTypeNames1) {
            atlasTypeName1 = candidate;
        }
        Set<String> atlasTypeNames2 = typeDefStore.getMappedAtlasTypeDefNames(omrsTypeName2, null);
        if (atlasTypeNames2.size() > 1) {
            log.warn("Found multiple mapped types, when expected only one: {}", omrsTypeName2);
        }
        String atlasTypeName2 = null;
        for (String candidate : atlasTypeNames2) {
            atlasTypeName2 = candidate;
        }

        // These look inverted - see comment above for rationale
        String attrNameForAtlas1 = omrs2.getAttributeName();
        String attrDescForAtlas1 = omrs2.getAttributeDescription();
        RelationshipEndCardinality cardinalityForAtlas1 = omrs2.getAttributeCardinality();
        String attrNameForAtlas2 = omrs1.getAttributeName();
        String attrDescForAtlas2 = omrs1.getAttributeDescription();
        RelationshipEndCardinality cardinalityForAtlas2 = omrs1.getAttributeCardinality();

        boolean fullyCovered = setupRelationshipEnd(
                omrsRelationshipDef,
                atlas1,
                atlasTypeName1,
                attrNameForAtlas1,
                attrDescForAtlas1,
                cardinalityForAtlas1
        );
        fullyCovered = fullyCovered && setupRelationshipEnd(
                omrsRelationshipDef,
                atlas2,
                atlasTypeName2,
                attrNameForAtlas2,
                attrDescForAtlas2,
                cardinalityForAtlas2
        );
        // TODO: do we need to ensure we put these endpoint mappings into typeDefStore?

        if (fullyCovered) {
            atlasRelationshipDef.setEndDef1(atlas1);
            atlasRelationshipDef.setEndDef2(atlas2);
        }

        return fullyCovered;

    }

    /**
     * Sets up the mapping of the provided OMRS relationship endpoints to the provided
     * Apache Atlas relationship endpoints.
     *
     * @param omrsRelationshipDef the OMRS RelationshipDef to add to Apache Atlas
     * @param atlasRelEnd the Apache Atlas endpoint of the relationship to setup
     * @param atlasTypeName the name of the Atlas type for this relationship end
     * @param atlasAttrName the name of the attribute on this end of the relationship (that points to the other end)
     * @param atlasAttrDescription the description of the attribute
     * @param atlasAttributeCardinality the cardinality of the attribute
     * @return boolean indicating success (true) or not (false) of the relationship endpoint setup
     */
    private static boolean setupRelationshipEnd(RelationshipDef omrsRelationshipDef,
                                                AtlasRelationshipEndDef atlasRelEnd,
                                                String atlasTypeName,
                                                String atlasAttrName,
                                                String atlasAttrDescription,
                                                RelationshipEndCardinality atlasAttributeCardinality) {
        boolean fullyCovered = true;
        if (atlasTypeName != null) {
            atlasRelEnd.setType(atlasTypeName);
            atlasRelEnd.setName(atlasAttrName);
            atlasRelEnd.setDescription(atlasAttrDescription);
            switch(atlasAttributeCardinality) {
                case ANY_NUMBER:
                    atlasRelEnd.setCardinality(AtlasStructDef.AtlasAttributeDef.Cardinality.SET);
                    break;
                case AT_MOST_ONE:
                    atlasRelEnd.setCardinality(AtlasStructDef.AtlasAttributeDef.Cardinality.SINGLE);
                    break;
                case UNKNOWN:
                    fullyCovered = false;
                    log.warn("Unable to determine cardinality of relationship end -- skipping the relationship: {}", omrsRelationshipDef);
                    break;
            }
        } else {
            fullyCovered = false;
        }
        return fullyCovered;
    }

    /**
     * Setup the cardinality of the relationship.
     *
     * @param relationshipTypeDef the Apache Atlas relationship definition on which to set cardinality
     */
    private static void setupCardinality(AtlasRelationshipDef relationshipTypeDef) {
        AtlasRelationshipEndDef e1 = relationshipTypeDef.getEndDef1();
        AtlasRelationshipEndDef e2 = relationshipTypeDef.getEndDef2();
        AtlasStructDef.AtlasAttributeDef.Cardinality c1 = e1.getCardinality();
        AtlasStructDef.AtlasAttributeDef.Cardinality c2 = e2.getCardinality();
        if ( (c1 == AtlasStructDef.AtlasAttributeDef.Cardinality.SINGLE && c2 == AtlasStructDef.AtlasAttributeDef.Cardinality.SET) ||
                (c2 == AtlasStructDef.AtlasAttributeDef.Cardinality.SINGLE && c1 == AtlasStructDef.AtlasAttributeDef.Cardinality.SET) ) {
            // if one end of the relationship is singular, and the other multiple, it's most likely a COMPOSITION
            // (the single end 'owns' the multiple objects related to it), however to be safe we'll go with AGGREGATION
            // TODO: may want to revisit and make this COMPOSITION, depending on the impact (?)
            relationshipTypeDef.setRelationshipCategory(AtlasRelationshipDef.RelationshipCategory.AGGREGATION);
            if (c1 == AtlasStructDef.AtlasAttributeDef.Cardinality.SINGLE) {
                e1.setIsContainer(true);
            } else {
                e2.setIsContainer(true);
            }
        } else {
            // catch-all is an ASSOCIATION
            relationshipTypeDef.setRelationshipCategory(AtlasRelationshipDef.RelationshipCategory.ASSOCIATION);
        }
    }

}
