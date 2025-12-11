module dev.mars.quorus.controller {
    requires java.logging;
    requires java.net.http;
    requires jdk.httpserver;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // Apache HttpComponents (Automatic module names derived from JAR filenames)
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;

    requires dev.mars.quorus.core;
    requires dev.mars.quorus.workflow;
    requires dev.mars.quorus.tenant;

    exports dev.mars.quorus.controller;
    exports dev.mars.quorus.controller.raft;
    exports dev.mars.quorus.controller.state;
    exports dev.mars.quorus.controller.service;

    opens dev.mars.quorus.controller to com.fasterxml.jackson.databind;
    opens dev.mars.quorus.controller.state to com.fasterxml.jackson.databind;
    opens dev.mars.quorus.controller.raft to com.fasterxml.jackson.databind;
}
