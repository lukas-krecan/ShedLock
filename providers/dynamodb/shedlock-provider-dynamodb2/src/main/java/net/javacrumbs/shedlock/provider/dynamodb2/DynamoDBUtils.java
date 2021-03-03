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
package net.javacrumbs.shedlock.provider.dynamodb2;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.ID;

public class DynamoDBUtils {

    /**
     * Creates a locking table with the given name.
     * <p>
     * This method does not check if a table with the given name exists already.
     *
     * @param ddbClient  v2 of DynamoDBClient
     * @param tableName  table to be used
     * @param throughput AWS {@link ProvisionedThroughput throughput requirements} for the given lock setup
     * @return           the table name
     *
     * @throws ResourceInUseException
     *         The operation conflicts with the resource's availability. You attempted to recreate an
     *         existing table.
     */
    public static String createLockTable(
            DynamoDbClient ddbClient,
            String tableName,
            ProvisionedThroughput throughput
    ) {

        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(KeySchemaElement.builder()
                        .attributeName(ID)
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(ID)
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .provisionedThroughput(throughput)
                .build();
        ddbClient.createTable(request);
        return tableName;
    }
}
