package app;

import java.util.HashMap;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * This function receives a request containing deviceID and location data and
 * uploads that data to the Location table under that deviceID.
 * <p>
 * 
 * Inputs take the form: {deviceID: string, passkey: string, longitude: number,
 * latitude: number, timestamp: integer}
 * <p>
 * Outputs take the form: "success" or an error message
 */
public class LocationPost implements RequestHandler<LocationPost.Request, String> {

    /**
     * Record representing the format of the request body
     */
    public record Request(String deviceID, String passkey, double longitude, double latitude, long timestamp) {
    }

    private static DynamoDbClient dbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build();

    @Override
    public String handleRequest(Request input, Context context) {
        // access table names
        String deviceTable = System.getenv("DEVICE_TABLE");
        String locationTable = System.getenv("LOCATION_TABLE");

        // check passkey
        HashMap<String, AttributeValue> map = new HashMap<>();
        map.put("deviceID", AttributeValue.fromS(input.deviceID));
        GetItemResponse deviceRes = dbClient.getItem(GetItemRequest.builder()
                .tableName(deviceTable)
                .key(map)
                .build());

        if (!deviceRes.hasItem()) {
            String errMsg = String.format("item with deviceID %s not found in %s table", input.deviceID, deviceTable);
            context.getLogger().log(errMsg);
            throw new RuntimeException(errMsg);
        }

        String passkey = deviceRes.item().get("deviceID").s();
        if (!passkey.equals(input.passkey)) {
            throw new RuntimeException("passwords do not match");
        }

        // put item
        map.put("logitude", AttributeValue.fromN(Double.toString(input.longitude)));
        map.put("latitude", AttributeValue.fromN(Double.toString(input.latitude)));
        map.put("timestamp", AttributeValue.fromN(Long.toString(input.timestamp)));

        dbClient.putItem(PutItemRequest.builder()
                .tableName(locationTable)
                .item(map)
                .build());

        return "success";
    }
}
