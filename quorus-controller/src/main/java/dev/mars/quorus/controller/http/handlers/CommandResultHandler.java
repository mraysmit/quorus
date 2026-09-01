/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.controller.http.handlers;

import dev.mars.quorus.controller.http.ErrorCode;
import dev.mars.quorus.controller.http.QuorusApiException;
import dev.mars.quorus.controller.state.CommandResult;
import io.vertx.ext.web.RoutingContext;

/** Converts deterministic state-machine rejections into explicit HTTP problems. */
final class CommandResultHandler {

    private CommandResultHandler() {
    }

    static boolean failIfRejected(RoutingContext context, CommandResult<?> result) {
        if (!(result instanceof CommandResult.Rejected<?> rejected)) {
            return false;
        }

        context.fail(rejectionException(rejected));
        return true;
    }

    static QuorusApiException rejectionException(CommandResult.Rejected<?> rejected) {
        ErrorCode errorCode = switch (rejected.code()) {
            case "DUPLICATE_ENTITY", "DEPENDENT_ENTITY_EXISTS", "INVALID_STATE_TRANSITION" -> ErrorCode.CONFLICT;
            default -> ErrorCode.VALIDATION_ERROR;
        };
        return new QuorusApiException(errorCode, rejected.message());
    }
}
