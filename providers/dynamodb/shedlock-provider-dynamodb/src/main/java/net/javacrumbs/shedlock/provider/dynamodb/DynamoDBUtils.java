/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.ID;

public class DynamoDBUtils {

    /**
     * Creates a locking table with the given name.
     * <p>
     * This method does not check if a table with the given name exists already.
     *
     * @param dynamodb   DynamoDB to be used
     * @param tableName  table to be used
     * @param throughput AWS {@link ProvisionedThroughput throughput requirements} for the given lock setup
     * @return           a {@link Table reference to the newly created table}
     *
     * @throws ResourceInUseException
     *         The operation conflicts with the resource's availability. You attempted to recreate an
     *         existing table.
     */
    public static Table createLockTable(
            AmazonDynamoDB dynamodb,
            String tableName,
            ProvisionedThroughput throughput
    ) {

        CreateTableRequest request = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(new KeySchemaElement(ID, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(ID, ScalarAttributeType.S))
                .withProvisionedThroughput(throughput);
        dynamodb.createTable(request).getTableDescription();
        return new DynamoDB(dynamodb).getTable(tableName);
    }
}
