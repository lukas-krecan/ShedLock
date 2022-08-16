package net.javacrumbs.shedlock.provider.couchbase.javaclient3;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;

public class DefaultCouchbaseLockConfiguration implements CouchbaseLockConfiguration {

    private final Bucket bucket;

    private final Collection collection;

    public DefaultCouchbaseLockConfiguration(Bucket bucket) {
        this(bucket, null);
    }

    public DefaultCouchbaseLockConfiguration(Bucket bucket, Collection collection) {
        this.bucket = bucket;
        this.collection = collection;
    }

    @Override
    public Bucket getBucket() {
        return this.bucket;
    }

    @Override
    public Collection getCollection() {
        if (this.collection == null) {
            return this.bucket.defaultCollection();
        }

        return this.collection;
    }
}

