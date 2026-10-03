/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.config;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

/** Reads the project version declared in a pom, for tests that compare reported versions with it. */
public final class PomVersion {

    private PomVersion() {
    }

    /** The {@code <version>} that is a direct child of {@code <project>} in the given pom. */
    public static String of(Path pom) throws Exception {
        Element project = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(pom.toFile()).getDocumentElement();
        for (Node child = project.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals("version")) {
                return element.getTextContent().trim();
            }
        }
        throw new IllegalStateException("No project version in " + pom.toAbsolutePath());
    }
}
