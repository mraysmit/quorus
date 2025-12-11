module dev.mars.quorus.api {
    requires java.logging;
    requires transitive dev.mars.quorus.core;
    requires transitive dev.mars.quorus.workflow;
    requires transitive dev.mars.quorus.tenant;

    // JSON/Jackson used by API DTOs/resources
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    exports dev.mars.quorus.api;
    exports dev.mars.quorus.api.config;
    exports dev.mars.quorus.api.health;
    exports dev.mars.quorus.api.service;
    exports dev.mars.quorus.api.dto;

    // Open DTOs for Jackson reflection
    opens dev.mars.quorus.api.dto to com.fasterxml.jackson.databind;
}
