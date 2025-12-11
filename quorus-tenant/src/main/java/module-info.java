module dev.mars.quorus.tenant {
    requires java.logging;
    requires transitive dev.mars.quorus.core;
    requires jakarta.validation;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    exports dev.mars.quorus.tenant.model;
    exports dev.mars.quorus.tenant.service;

    // Allow Jackson to reflectively access model classes if needed
    opens dev.mars.quorus.tenant.model to com.fasterxml.jackson.databind;
}
