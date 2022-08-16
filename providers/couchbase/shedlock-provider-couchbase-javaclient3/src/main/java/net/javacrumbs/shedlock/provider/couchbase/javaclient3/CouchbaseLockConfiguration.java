package net.javacrumbs.shedlock.provider.couchbase.javaclient3;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;

public interface CouchbaseLockConfiguration {

    Bucket getBucket();

    Collection getCollection();

    default Collection collection() {
        if (this.getCollection() == null) {
            if (this.getBucket() == null) {
                throw new IllegalArgumentException("Bucket can not be null.");
            }

            return this.getBucket().defaultCollection();
        }

        return this.getCollection();
    }
}

