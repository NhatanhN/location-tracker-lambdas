package app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public class DevicePost implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /**
     * Record representing the format of the request body
     * 
     * @param passkey The user-specified passkey associated with the device
     */
    public record Request(String passkey) {
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
        
        // Access device table name
        String deviceTable = System.getenv("DEVICE_TABLE");

        // create new entry into table
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("passkey", AttributeValue.fromS(input.passkey()));
        item.put("deviceID", AttributeValue.fromS(java.util.UUID.randomUUID().toString()));

        dbClient.putItem(PutItemRequest.builder()
                .tableName(deviceTable)
                .item(item)
                .build());
                
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withBody(String.format("{ \"deviceID\": \"%s\"}", item.get("deviceID").s()));
    }
}
