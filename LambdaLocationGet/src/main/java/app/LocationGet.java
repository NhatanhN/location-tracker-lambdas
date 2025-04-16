package app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * This function receives a request containing a deviceID and passkey and
 * returns an array of locations associated with that deviceID. Since expects
 * a request body, the method must be of type post.
 * <p>
 * 
 * Inputs take the form: { deviceID: string, passkey: string }
 * <p>
 * Outputs take the form: {
 * locations: [{ latitude: float, longitude: float, timestamp: long }, ...]
 * }
 */
public class LocationGet implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /**
     * Record representing the format of the request body
     * 
     * @param deviceID The user-specified deviceID associated with the device
     * @param passkey The user-specified passkey associated with the device
     */
    public record Request(String deviceID, String passkey) {
    }

    private static DynamoDbClient dbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String body;
        if (event.getIsBase64Encoded()) {
            byte[] decoded = Base64.getDecoder().decode(event.getBody());
            body = new String(decoded, StandardCharsets.UTF_8);
        } else {
            body = event.getBody();
        }
        
        ObjectMapper mapper = new ObjectMapper();
        Request input;

        try {
            input = mapper.readValue(body, Request.class);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("Could not parse body");
        }
        
        // access table names
        String deviceTable = System.getenv("DEVICE_TABLE");
        String locationTable = System.getenv("LOCATION_TABLE");

        // check passkey
        HashMap<String, AttributeValue> map = new HashMap<>();
        map.put("deviceID", AttributeValue.fromS(input.deviceID()));
        GetItemResponse deviceRes = dbClient.getItem(GetItemRequest.builder()
                .tableName(deviceTable)
                .key(map)
                .build());

        if (!deviceRes.hasItem()) {
            String errMsg = String.format("item with deviceID %s not found in %s table", input.deviceID(), deviceTable);
            context.getLogger().log(errMsg);
            throw new RuntimeException(errMsg);
        }

        String passkey = deviceRes.item().get("passkey").s();
        if (!passkey.equals(input.passkey())) {
            throw new RuntimeException("passwords do not match");
        }

        // get locations
        map.clear();
        map.put(":deviceID", AttributeValue.fromS(input.deviceID()));
        ScanRequest locReq = ScanRequest.builder()
                .tableName(locationTable)
                .filterExpression("deviceID = :deviceID")
                .expressionAttributeValues(map)
                .build();

        List<Map<String, AttributeValue>> items = dbClient.scan(locReq).items();

        // return locations
        if (items.size() == 0)
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody("{}");

        StringBuilder json = new StringBuilder("{ locations: [");
        for (Map<String, AttributeValue> i : items) {
            json.append(String.format("{\"longitude\": \"%s\", \"latitude\": \"%s\", \"timestamp\": \"%s\"}, ",
                    i.get("logitude").n(), i.get("latitude").n(), i.get("timestamp").n()));
        }
        json.deleteCharAt(json.length() - 2);
        json.append("] }");

        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withBody(json.toString());
    }

}
