/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.stores;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model.MappingFromFile;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.AttributeTypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.AttributeTypeDefCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.EnumDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.EnumElementDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Store of implemented AttributeTypeDefs for the repository.
 */
public class AttributeTypeDefStore {

    private static final Logger log = LoggerFactory.getLogger(AttributeTypeDefStore.class);

    private Map<String, String> omrsNameToAtlasName;
    private Map<String, String> atlasNameToOmrsName;
    private Map<String, AttributeTypeDef> omrsGuidToTypeDef;
    private Map<String, String> omrsNameToGuid;

    private Map<String, AttributeTypeDef> unimplementedTypeDefs;
    private Map<String, Map<String, Set<String>>> atlasNameToElementMap;
    private Map<String, Map<String, Set<String>>> omrsNameToElementMap;

    private ObjectMapper mapper;

    public AttributeTypeDefStore() {
        this.omrsNameToAtlasName = new HashMap<>();
        this.atlasNameToOmrsName = new HashMap<>();
        this.omrsGuidToTypeDef = new HashMap<>();
        this.omrsNameToGuid = new HashMap<>();
        this.unimplementedTypeDefs = new HashMap<>();
        this.atlasNameToElementMap = new HashMap<>();
        this.omrsNameToElementMap = new HashMap<>();
        this.mapper = new ObjectMapper();
        loadMappings();
    }

    /**
     * Loads TypeDef mappings defined through a resources file included in the .jar file.
     */
    private void loadMappings() {
        ClassPathResource mappingResource = new ClassPathResource("EnumDefMappings.json");
        try {
            InputStream stream = mappingResource.getInputStream();
            List<MappingFromFile> mappings = mapper.readValue(stream, new TypeReference<List<MappingFromFile>>(){});
            for (MappingFromFile mapping : mappings) {
                String atlasName = mapping.getAtlasName();
                String omrsName = mapping.getOMRSName();
                omrsNameToAtlasName.put(omrsName, atlasName);
                atlasNameToOmrsName.put(atlasName, omrsName);
                List<MappingFromFile> elements = mapping.getPropertyMappings();
                if (elements != null) {
                    Map<String, Set<String>> elementMapOmrsToAtlas = new HashMap<>();
                    Map<String, Set<String>> elementMapAtlasToOmrs = new HashMap<>();
                    for (MappingFromFile element : elements) {
                        String atlasElement = element.getAtlasName();
                        String omrsElement = element.getOMRSName();
                        if (!elementMapOmrsToAtlas.containsKey(omrsElement)) {
                            elementMapOmrsToAtlas.put(omrsElement, new TreeSet<>());
                        }
                        elementMapOmrsToAtlas.get(omrsElement).add(atlasElement);
                        if (!elementMapAtlasToOmrs.containsKey(atlasElement)) {
                            elementMapAtlasToOmrs.put(atlasElement, new TreeSet<>());
                        }
                        elementMapAtlasToOmrs.get(atlasElement).add(omrsElement);
                    }
                    atlasNameToElementMap.put(atlasName, elementMapAtlasToOmrs);
                    omrsNameToElementMap.put(omrsName, elementMapOmrsToAtlas);
                }
            }
        } catch (IOException e) {
            log.error("Unable to load mapping file EnumDefMappings.json from jar file -- no mappings will exist.");
        }
    }

    /**
     * Indicates whether the provided OMRS TypeDef is mapped to an Apache Atlas TypeDef.
     *
     * @param omrsName name of the OMRS TypeDef
     * @return boolean
     */
    public boolean isTypeDefMapped(String omrsName) {
        return omrsNameToAtlasName.containsKey(omrsName);
    }

    /**
     * Retrieves an implemented AttributeTypeDef by its GUID.
     *
     * @param guid of the type definition
     * @return AttributeTypeDef
     */
    public AttributeTypeDef getAttributeTypeDefByGUID(String guid) {
        return getAttributeTypeDefByGUID(guid, true);
    }

    /**
     * Retrieves an implemented AttributeTypeDef by its GUID.
     *
     * @param guid of the type definition
     * @param warnIfNotFound whether to log a warning if GUID is not known (true) or not (false).
     * @return AttributeTypeDef
     */
    private AttributeTypeDef getAttributeTypeDefByGUID(String guid, boolean warnIfNotFound) {
        if (omrsGuidToTypeDef.containsKey(guid)) {
            return omrsGuidToTypeDef.get(guid);
        } else {
            if (warnIfNotFound && log.isWarnEnabled()) {
                log.warn("Unable to find OMRS AttributeTypeDef by GUID: {}", guid);
            }
            return null;
        }
    }

    /**
     * Retrieves an implemented AttributeTypeDef by its name.
     *
     * @param name of the type definition
     * @return AttributeTypeDef
     */
    public AttributeTypeDef getAttributeTypeDefByName(String name) {
        return getAttributeTypeDefByName(name, true);
    }

    /**
     * Retrieves an implemented AttributeTypeDef by its name.
     *
     * @param name of the type definition
     * @param warnIfNotFound whether to log a warning if name is not known (true) or not (false).
     * @return AttributeTypeDef
     */
    private AttributeTypeDef getAttributeTypeDefByName(String name, boolean warnIfNotFound) {
        if (omrsNameToGuid.containsKey(name)) {
            String guid = omrsNameToGuid.get(name);
            return getAttributeTypeDefByGUID(guid, warnIfNotFound);
        } else {
            if (warnIfNotFound && log.isWarnEnabled()) {
                log.warn("Unable to find OMRS AttributeTypeDef by Name: {}", name);
            }
            return null;
        }
    }

    /**
     * Retrieves a map from Apache Atlas enum value to OMRS enum value for the provided Apache Atlas TypeDef name,
     * or null if there are no mappings (or no enum elements).
     *
     * @param atlasName the name of the Apache Atlas TypeDef
     * @return {@code Map<String, Set<String>>}
     */
    public Map<String, Set<String>> getElementMappingsForAtlasTypeDef(String atlasName) {
        return atlasNameToElementMap.getOrDefault(atlasName, null);
    }

    /**
     * Retrieves a map from OMRS enum value to Apache Atlas enum value for the provided OMRS TypeDef name, or null
     * if there are no mappings (or no enum elements).
     *
     * @param omrsName the name of the OMRS TypeDef
     * @return {@code Map<String, Set<String>>} keyed by OMRS name to all of the mapped Atlas values
     */
    public Map<String, Set<String>> getElementMappingsForOMRSTypeDef(String omrsName) {
        return omrsNameToElementMap.getOrDefault(omrsName, null);
    }

    /**
     * Retrieves the Apache Atlas TypeDef name that is mapped to the provided OMRS TypeDef name, or null if there is
     * no mapping.
     *
     * @param omrsName the name of the OMRS TypeDef
     * @return String
     */
    public String getMappedAtlasTypeDefName(String omrsName) {
        if (isTypeDefMapped(omrsName)) {
            return omrsNameToAtlasName.get(omrsName);
        } else {
            return null;
        }
    }

    /**
     * Retrieves the OMRS TypeDef name that is mapped to the provided Apache Atlas TypeDef name, or null if there is
     * no mapping.
     *
     * @param atlasName the name of the Apache Atlas TypeDef
     * @return String
     */
    public String getMappedOMRSTypeDefName(String atlasName) {
        return atlasNameToOmrsName.getOrDefault(atlasName, null);
    }

    /**
     * Adds the provided TypeDef to the list of those that are implemented in the repository.
     *
     * @param typeDef an implemented type definition
     */
    public void addTypeDef(AttributeTypeDef typeDef) {
        String guid = typeDef.getGUID();
        String name = typeDef.getName();
        omrsGuidToTypeDef.put(guid, typeDef);
        omrsNameToGuid.put(name, guid);
        if (!omrsNameToAtlasName.containsKey(name)) {
            omrsNameToAtlasName.put(name, name);
            atlasNameToOmrsName.put(name, name);
        }
        // If it is an enumeration that is not otherwise mapped, save the one-to-one mapping
        if (typeDef.getCategory().equals(AttributeTypeDefCategory.ENUM_DEF) && !omrsNameToElementMap.containsKey(name)) {
            EnumDef enumDef = (EnumDef) typeDef;
            Map<String, Set<String>> elementMap = new HashMap<>();
            for (EnumElementDef elementDef : enumDef.getElementDefs()) {
                Set<String> set = new TreeSet<>();
                set.add(elementDef.getValue());
                elementMap.put(elementDef.getValue(), set);
            }
            omrsNameToElementMap.put(name, elementMap);
            atlasNameToElementMap.put(name, elementMap);
        }
    }

    /**
     * Adds the provided TypeDef to the list of those that are not implemented in the repository.
     * (Still needed for tracking and inheritance.)
     *
     * @param typeDef an unimplemented type definition
     */
    public void addUnimplementedTypeDef(AttributeTypeDef typeDef) {
        String guid = typeDef.getGUID();
        unimplementedTypeDefs.put(guid, typeDef);
    }

    /**
     * Retrieves a listing of all of the implemented type definitions for this repository.
     *
     * @return {@code List<TypeDef>}
     */
    public List<AttributeTypeDef> getAllAttributeTypeDefs() {
        return new ArrayList<>(omrsGuidToTypeDef.values());
    }

}
