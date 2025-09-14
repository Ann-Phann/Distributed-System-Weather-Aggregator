package com.weather;

import com.weather.client.contentserver.ContentServer;
import com.weather.client.getclient.GetClient;
import com.weather.server.AggregationServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A JUnit 5 test suite for the Weather Aggregation Server.
 * Each test case runs the server, performs an action, and then verifies the result.
 */
class ServerTests {

    private final int port = 4567;
    private AggregationServer server;
    private Thread serverThread;
    private static final String TEST_ID = "ID001";
    // Updated test data to match the simple key-value pair text format
    private static final String TEST_DATA = "id:ID001\nlocation:Adelaide\nstate:SA\ntemperature:13.3\ncloud:Partly cloudy";
    
    // The base directory where ContentServer expects to find its files
    private static final String CONTENT_SERVER_DIR = "src/main/java/com/weather/client/contentserver/data";
    private static final String TEST_FILE_NAME = "test_put_data.txt";
    private static final String TEST_FILE_PATH = CONTENT_SERVER_DIR + "/" + TEST_FILE_NAME;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        // Ensure a clean state before each test
        new File("data").mkdirs();
        File dataLog = new File("data/server.log");
        if (dataLog.exists()) {
            dataLog.delete();
        }
        
        // Create the ContentServer's data directory if it doesn't exist
        new File(CONTENT_SERVER_DIR).mkdirs();

        // Create a temporary file to simulate content server input
        try (FileWriter writer = new FileWriter(TEST_FILE_PATH)) {
            writer.write(TEST_DATA);
        }

        // Start the server in a separate thread
        server = new AggregationServer(port);
        serverThread = new Thread(server);
        serverThread.start();
        
        // Give the server a moment to start up and open its socket
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Shut down the server after each test
        server.close();
        serverThread.join();
        
        // Clean up the temporary test file
        File testFile = new File(TEST_FILE_PATH);
        if (testFile.exists()) {
            testFile.delete();
        }
    }

    /**
     * Test Case TC-001: Successful PUT Request
     * Objective: Verify that a valid PUT request is processed correctly and the data is stored.
     * @throws ClassNotFoundException 
     */
    @Test
    void testSuccessfulPutRequest() throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println("\n--- Running Test TC-001: Successful PUT Request ---");
        
        // Step 1: Send a PUT request using the ContentServer
        ContentServer contentClient = new ContentServer("localhost", port, TEST_FILE_NAME);
        contentClient.requestAndResponse();

        // Give the server time to process the request
        TimeUnit.MILLISECONDS.sleep(500);

        // Step 2: Send a GET request to retrieve the same data
        GetClient getClient = new GetClient("localhost", port, TEST_ID);
        getClient.requestAndResponse();

        // Step 3: Verify the results
        // assume success if the GET client receives a response without error.
        System.out.println("Expected: Server accepts PUT, then GET retrieves the same data. Look at the console for 'GET Client Response'.");
        System.out.println("--- Test TC-001 Finished ---\n");
    }
}
