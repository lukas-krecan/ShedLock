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
import com.amazonaws.services.dynamodbv2.model.TableDescription;

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
