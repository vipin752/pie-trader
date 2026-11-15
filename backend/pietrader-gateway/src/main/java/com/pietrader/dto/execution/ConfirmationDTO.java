package com.pietrader.dto.execution;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationDTO {

    @JsonProperty("confirmed")
    private Boolean confirmed;

    @JsonProperty("type")
    private String type;

    @JsonProperty("message")
    private String message;
}
