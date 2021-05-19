/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.apache.atlas.repositoryconnector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/**
 * Represents a single TypeDef mapping, to safely parse TypeDefMappings.json resource.
 */
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonTypeName("TypeDefMapping")
public class MappingFromFile {

    /**
     * The name of the TypeDef within Apache Atlas.
     */
    private String atlas;

    /**
     * The name of the TypeDef within OMRS.
     */
    private String omrs;

    /**
     * The prefix to use for any generated OMRS TypeDefs based on a singular Apache Atlas type.
     */
    private String prefix;

    /**
     * An array of mappings between Apache Atlas and OMRS property names for the TypeDef.
     */
    private List<MappingFromFile> propertyMappings;

    /**
     * An array of mappings between Apache Atlas endpoint property names and OMRS endpoint property names,
     * for any relationship TypeDefs
     */
    private List<MappingFromFile> endpointMappings;

    /**
     * Indicates whether the endpoints for an endpointMapping should be inverted when translating between
     * Egeria and Apache Atlas
     */
    private boolean invertedEndpoints = false;

    @JsonProperty("atlas") public String getAtlasName() { return this.atlas; }
    @JsonProperty("atlas") public void setAtlasName(String atlas) { this.atlas = atlas; }

    @JsonProperty("omrs") public String getOMRSName() { return this.omrs; }
    @JsonProperty("omrs") public void setOMRSName(String omrs) { this.omrs = omrs; }

    @JsonProperty("prefix") public String getPrefix() { return this.prefix; }
    @JsonProperty("prefix") public void setPrefix(String prefix) { this.prefix = prefix; }

    @JsonProperty("propertyMappings") public List<MappingFromFile> getPropertyMappings() { return this.propertyMappings; }
    @JsonProperty("propertyMappings") public void setPropertyMappings(List<MappingFromFile> propertyMappings) { this.propertyMappings = propertyMappings; }

    @JsonProperty("endpointMappings") public List<MappingFromFile> getEndpointMappings() { return this.endpointMappings; }
    @JsonProperty("endpointMappings") public void setEndpointMappings(List<MappingFromFile> endpointMappings) { this.endpointMappings = endpointMappings; }

    @JsonProperty("invertedEndpoints") public boolean getInvertedEndpoints() { return invertedEndpoints; }
    @JsonProperty("invertedEndpoints") public void setInvertedEndpoints(boolean invertEndpoints) { this.invertedEndpoints = invertedEndpoints; }

    @JsonIgnore public boolean isGeneratedType() { return this.prefix != null; }

}
