/**
 * ShedLock AWS S3 provider module (SDK v2).
 *
 * This module provides AWS S3-based lock provider implementation
 * using the AWS SDK for Java v2.
 */
module net.javacrumbs.shedlock.provider.s3v2 {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires software.amazon.awssdk.services.s3;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.awscore;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.s3v2;
}
