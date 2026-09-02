/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package dev.mars.quorus.controller.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/** Immutable ownership and deadline context used by transfer operations. */
public record TransferOperationalContext(
        String businessService,
        String owner,
        String criticality,
        String environment,
        String processingDate,
        Instant expectedStartAt,
        Instant requiredCompletionAt,
        String runbookUrl) implements Serializable {

    @JsonCreator
    public TransferOperationalContext(
            @JsonProperty("businessService") String businessService,
            @JsonProperty("owner") String owner,
            @JsonProperty("criticality") String criticality,
            @JsonProperty("environment") String environment,
            @JsonProperty("processingDate") String processingDate,
            @JsonProperty("expectedStartAt") Instant expectedStartAt,
            @JsonProperty("requiredCompletionAt") Instant requiredCompletionAt,
            @JsonProperty("runbookUrl") String runbookUrl) {
        this.businessService = businessService;
        this.owner = owner;
        this.criticality = criticality;
        this.environment = environment;
        this.processingDate = processingDate;
        this.expectedStartAt = expectedStartAt;
        this.requiredCompletionAt = requiredCompletionAt;
        this.runbookUrl = runbookUrl;
    }

    public static TransferOperationalContext fromMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        TransferOperationalContext context = new TransferOperationalContext(
                value(metadata, "businessService"),
                value(metadata, "owner"),
                value(metadata, "criticality"),
                value(metadata, "environment"),
                value(metadata, "processingDate"),
                instant(metadata, "expectedStartAt"),
                instant(metadata, "requiredCompletionAt"),
                value(metadata, "runbookUrl"));
        return context.isEmpty() ? null : context;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return businessService == null && owner == null && criticality == null && environment == null
                && processingDate == null && expectedStartAt == null && requiredCompletionAt == null
                && runbookUrl == null;
    }

    private static String value(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant instant(Map<String, String> metadata, String key) {
        String value = value(metadata, key);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 instant", invalid);
        }
    }
}
