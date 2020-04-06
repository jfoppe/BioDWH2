package de.unibi.agbi.biodwh2.drugcentral.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import de.unibi.agbi.biodwh2.core.model.graph.GraphProperty;
import de.unibi.agbi.biodwh2.core.model.graph.NodeLabels;

@JsonPropertyOrder(value = {"code", "description"})
@NodeLabels({"ObExclusivityCode"})
public final class ObExclusivityCode {
    @JsonProperty("code")
    @GraphProperty("code")
    public String code;
    @JsonProperty("description")
    @GraphProperty("description")
    public String description;

}
