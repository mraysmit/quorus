module dev.mars.quorus.core {
    requires java.logging;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.jcraft.jsch;
    requires jdk.httpserver;  // For integration tests using embedded HTTP server

    // Public API exports
    exports dev.mars.quorus.core;
    exports dev.mars.quorus.core.exceptions;
    exports dev.mars.quorus.storage;
    exports dev.mars.quorus.network;
    exports dev.mars.quorus.protocol;
    exports dev.mars.quorus.transfer;
    exports dev.mars.quorus.agent;
    exports dev.mars.quorus.config;

    // If Jackson is used reflectively on these packages, open them to databind
    opens dev.mars.quorus.core to com.fasterxml.jackson.databind;
    opens dev.mars.quorus.agent to com.fasterxml.jackson.databind;
}
