/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import java.net.URI;
import java.util.List;

/**
 * The agent's configured controllers and the one it currently sends to (register item ENG-27).
 * The agent only ever talks to a configured controller: a leader hint selects among them and can
 * never introduce a new address. Safe for use from several threads.
 */
final class ControllerEndpoints {

    private final List<String> urls;
    private volatile int current;

    ControllerEndpoints(List<String> urls) {
        this.urls = List.copyOf(urls);
    }

    int size() {
        return urls.size();
    }

    /** The API base URL requests go to now. */
    String current() {
        return urls.get(current);
    }

    /**
     * Moves away from the controller {@code from}, which refused a write: to the leader it named if
     * that is a configured controller, otherwise to the one configured after {@code from}.
     *
     * @param leaderHint the leader's API base URL as sent in {@code X-Quorus-Leader}, or null
     * @return true if requests now go to a different controller
     */
    boolean leaveFollower(String from, String leaderHint) {
        int left = urls.indexOf(from);
        int hinted = indexOfSameOrigin(leaderHint);
        int next = hinted >= 0 && hinted != left ? hinted : (left + 1) % urls.size();
        if (next == left) {
            return false;
        }
        current = next;
        return true;
    }

    /** Moves from the controller {@code from}, which could not be reached, to the one configured after it. */
    void leaveUnreachable(String from) {
        leaveFollower(from, null);
    }

    /** The configured controller with the hint's scheme, host and port, or -1. */
    private int indexOfSameOrigin(String leaderHint) {
        if (leaderHint == null) {
            return -1;
        }
        String origin = origin(leaderHint);
        for (int i = 0; i < urls.size(); i++) {
            if (origin(urls.get(i)).equals(origin)) {
                return i;
            }
        }
        return -1;
    }

    private static String origin(String url) {
        try {
            URI uri = URI.create(url.trim());
            return (uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort()).toLowerCase();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
