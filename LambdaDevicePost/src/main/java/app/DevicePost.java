package app;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * This function receives a request containing a passkey, inserts a new entry
 * into the Device table using it, and returns the deviceID associated with the
 * new entry.
 * <p>
 * 
 * Inputs take the form: { passkey: string }
 * <p>
 * Outputs take the form: { deviceID: string }
 */
public class DevicePost implements RequestHandler<DevicePost.Request, String> {

    /**
     * Record representing the format of the request body
     * 
     * @param passkey The user-specified passkey associated with the device
     */
    public record Request(String passkey) {
    }

    private static DynamoDbClient dbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build();

    @Override
    public String handleRequest(Request input, Context context) {
        // Access device table name
        String deviceTable = System.getenv("DEVICE_TABLE");

        if (deviceTable == null || deviceTable.isEmpty()) {
            throw new IllegalArgumentException("DEVICE_TABLE environment variable is not set");
        }

        // create new entry into table
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("passkey", AttributeValue.fromS(input.passkey));
        item.put("deviceID", AttributeValue.fromS(java.util.UUID.randomUUID().toString()));

        dbClient.putItem(PutItemRequest.builder()
                .tableName(deviceTable)
                .item(item)
                .build());

        return String.format("{ deviceID: %s }", item.get("deviceID").s());
    }
}
