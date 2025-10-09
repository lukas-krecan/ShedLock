/**
 * ShedLock DynamoDB provider module.
 *
 * This module provides Amazon DynamoDB-based lock provider implementation
 * using AWS SDK v2.
 */
@SuppressWarnings("module")
module net.javacrumbs.shedlock.provider.dynamodb2 {
    requires net.javacrumbs.shedlock.core;
    requires software.amazon.awssdk.services.dynamodb;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.utils;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.dynamodb2;
}
