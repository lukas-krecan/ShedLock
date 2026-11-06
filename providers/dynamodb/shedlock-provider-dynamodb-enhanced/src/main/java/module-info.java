/**
 * ShedLock DynamoDB enhanced-client provider module.
 *
 * This module provides Amazon DynamoDB-based locking using the AWS SDK v2 enhanced client.
 */
@SuppressWarnings("module")
module net.javacrumbs.shedlock.provider.dynamodb.enhanced {
    requires net.javacrumbs.shedlock.core;
    requires software.amazon.awssdk.enhanced.dynamodb;
    requires software.amazon.awssdk.services.dynamodb;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.utils;

    exports net.javacrumbs.shedlock.provider.dynamodb.enhanced;
}
