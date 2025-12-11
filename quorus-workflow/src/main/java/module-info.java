module dev.mars.quorus.workflow {
    requires java.logging;
    requires transitive dev.mars.quorus.core;

    // Third-party libraries used in main sources
    requires org.yaml.snakeyaml;
    requires com.networknt.schema;
    requires spring.expression;

    exports dev.mars.quorus.workflow;
    // opens dev.mars.quorus.workflow to com.fasterxml.jackson.databind;
}
