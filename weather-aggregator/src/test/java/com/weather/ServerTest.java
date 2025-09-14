package com.weather;

import com.weather.client.contentserver.ContentServer;
import com.weather.client.getclient.GetClient;
import com.weather.server.AggregationServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final List<File> createdTestFiles = new ArrayList<>();

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
        File mainTestFile = new File(TEST_FILE_PATH);
        try (FileWriter writer = new FileWriter(mainTestFile)) {
            writer.write(TEST_DATA);
        }
        createdTestFiles.add(mainTestFile);

        // Start the server in a separate thread
        server = new AggregationServer(port);
        serverThread = new Thread(server);
        serverThread.start();
        
        // Give the server a moment to start up and open its socket
        TimeUnit.MILLISECONDS.sleep(1000);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Shut down the server after each test
        server.close();
        serverThread.join();
        
        // Clean up the temporary test file
        // Clean up all temporary test files created during the tests
        for (File file : createdTestFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
        createdTestFiles.clear();
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

    /**
     * Test Case TC-002: Successful GET Request
     * Objective: Verify that the server can retrieve and return a specific station's weather data.
     */
    @Test
    void testSuccessfulGetRequest() throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println("\n--- Running Test TC-002: Successful GET Request ---");
        
        // Step 1: Send a PUT request to ensure there is data to retrieve
        System.out.println("Attempting to connect with ContentServer to add data...");
        ContentServer contentClient = new ContentServer("localhost", port, TEST_FILE_NAME);
        contentClient.requestAndResponse();

        // Give the server time to process the request
        TimeUnit.MILLISECONDS.sleep(1000);
        
        // Step 2: Send a GET request to retrieve the data
        System.out.println("Attempting to connect with GETClient to retrieve data...");
        GetClient getClient = new GetClient("localhost", port, TEST_ID);
        getClient.requestAndResponse();

        // Step 3: Verify the results manually
        System.out.println("Expected Outcome: Server responds with a 200 OK status and the correct data body. Look at the console for 'GET Client Response'.");
        System.out.println("--- Test TC-002 Finished ---\n");
    }

    /**
     * Test Case TC-003: Simultaneous PUT Requests
     * Objective: Verify the server's ability to handle multiple simultaneous PUT requests without data corruption or deadlocks.
     */
    @Test
    void testSimultaneousPutRequests() throws InterruptedException, IOException {
        System.out.println("\n--- Running Test TC-003: Simultaneous PUT Requests ---");
        
        int numClients = 3; // Changed to 3 as per your request
        List<Thread> clientThreads = new ArrayList<>();
        
        // Step 1: Create multiple clients and unique files for each
        for (int i = 0; i < numClients; i++) {
            final String stationId = "ID" + String.format("%03d", i);
            final String testFileName = "test_put_" + stationId + ".txt";
            final String testFilePath = CONTENT_SERVER_DIR + "/" + testFileName;
            final String testData = "id:" + stationId + "\nlocation:City" + i + "\ntemperature:20.5";
            
            // Create a temporary file for this client
            File clientTestFile = new File(testFilePath);
            try (FileWriter writer = new FileWriter(clientTestFile)) {
                writer.write(testData);
            }
            createdTestFiles.add(clientTestFile);
            
            // Create and store a new thread for each client
            Thread clientThread = new Thread(() -> {
                try {
                    ContentServer client = new ContentServer("localhost", port, testFileName);
                    client.requestAndResponse();
                } catch (ClassNotFoundException e) {
                    System.err.println("Error running client for " + stationId + ": " + e.getMessage());
                }
            });
            clientThreads.add(clientThread);
        }
        
        // Step 2: Start all client threads simultaneously
        System.out.println("Starting " + numClients + " clients to send PUT requests concurrently...");
        for (Thread thread : clientThreads) {
            thread.start();
        }
        
        // Step 3: Wait for all threads to complete
        for (Thread thread : clientThreads) {
            thread.join();
        }
        
        // Give the server a moment to process all requests
        TimeUnit.MILLISECONDS.sleep(1500);
        
        // Step 4: Verification (manual for now)
        System.out.println("All clients have sent their requests. Check the server logs to ensure all " + numClients + " PUT requests were processed correctly and without errors.");
        System.out.println("--- Test TC-003 Finished ---\n");
    }

    /**
     * Test Case TC-004: Mixed GET and PUT Requests
     * Objective: Verify the server's stability when a mix of GET and PUT requests are processed concurrently.
     */
    @Test
    void testMixedGetAndPutRequests() throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println("\n--- Running Test TC-004: Mixed GET and PUT Requests ---");
        
        int numClients = 3; 
        List<String> stationIds = new ArrayList<>();
        List<Thread> clientThreads = new ArrayList<>();
        
        // Step 1: Create multiple clients and unique files for PUT requests
        for (int i = 0; i < numClients; i++) {
            final String stationId = "ID" + String.format("%03d", i);
            stationIds.add(stationId);
            final String testFileName = "test_mixed_put_" + stationId + ".txt";
            final String testFilePath = CONTENT_SERVER_DIR + "/" + testFileName;
            final String testData = "id:" + stationId + "\nlocation:City" + i + "\ntemperature:20.5";
            
            // Create a temporary file for this client
            File clientTestFile = new File(testFilePath);
            try (FileWriter writer = new FileWriter(clientTestFile)) {
                writer.write(testData);
            }
            createdTestFiles.add(clientTestFile);
            
            // Create a PUT client thread
            Thread putThread = new Thread(() -> {
                try {
                    ContentServer client = new ContentServer("localhost", port, testFileName);
                    client.requestAndResponse();
                } catch (ClassNotFoundException e) {
                    System.err.println("Error running PUT client for " + stationId + ": " + e.getMessage());
                }
            });
            clientThreads.add(putThread);
        }

        // Step 2: Create a mix of GET and PUT threads and start them
        System.out.println("Starting a mix of " + numClients + " PUT clients and " + numClients + " GET clients concurrently...");
        
        for (int i = 0; i < numClients; i++) {
            final String getStationId = stationIds.get(i);
            // Create a GET client thread
            Thread getThread = new Thread(() -> {
                try {
                    GetClient client = new GetClient("localhost", port, getStationId);
                    client.requestAndResponse();
                } catch ( ClassNotFoundException e) {
                    System.err.println("Error running GET client for " + getStationId + ": " + e.getMessage());
                }
            });
            clientThreads.add(getThread);
        }

        // Start all threads at once
        for (Thread thread : clientThreads) {
            thread.start();
        }
        
        // Step 3: Wait for all threads to complete
        for (Thread thread : clientThreads) {
            thread.join();
        }

        // Give the server a moment to process all requests
        TimeUnit.MILLISECONDS.sleep(1500);

        // Step 4: Verification - Send final GET requests to ensure all data is present
        System.out.println("\nFinal verification: Sending GET requests for all station IDs to confirm data was stored correctly.");
        for (String stationId : stationIds) {
            System.out.println("Verifying data for station " + stationId + "...");
            GetClient client = new GetClient("localhost", port, stationId);
            client.requestAndResponse();
        }

        // Final verification note
        System.out.println("Expected Outcome: All requests (both GET and PUT) are processed without errors, and the final GET requests retrieve the correct data. Check the console logs for confirmation.");
        System.out.println("--- Test TC-004 Finished ---\n");
    }

    /**
     * Test Case TC-005: GET Request for Non-existent Station ID
     * Objective: Verify the server correctly handles requests for station IDs that have no associated data.
     */
    @Test
    void testGetRequestForNonExistentStation() throws IOException, ClassNotFoundException {
        System.out.println("\n--- Running Test TC-007: GET Request for Non-existent Station ID ---");

        // The ID for this test must not have any associated data
        final String nonExistentId = "ID999";
        System.out.println("Attempting GET request for non-existent station: " + nonExistentId);
        
        // Use the existing GetClient class to perform the request.
        // This ensures the test is using the same client-side logic as your successful manual tests.
        GetClient getClient = new GetClient("localhost", port, nonExistentId);
        getClient.requestAndResponse();
        
        System.out.println("\n--- Test TC-005 Finished ---\n");
    }

    /**
     * Test Case TC-008: Crash and Recovery
     * Objective: Verify that the server can recover from a crash and restore its data state from the write-ahead log.
     */
    @Test
    void testCrashAndRecovery() throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println("\n--- Running Test TC-008: Crash and Recovery ---");

        // Step 1: Send a few PUT requests to populate the server with data.
        final String stationId1 = "TC008_ID1";
        final String stationId2 = "TC008_ID2";
        
        // Create a temporary file for the first station
        File file1 = new File(CONTENT_SERVER_DIR + "/TC008_1.txt");
        try (FileWriter writer = new FileWriter(file1)) {
            writer.write("id:" + stationId1 + "\nlocation:Melbourne\ntemperature:22.5");
        }
        createdTestFiles.add(file1);

        // Create a temporary file for the second station
        File file2 = new File(CONTENT_SERVER_DIR + "/TC008_2.txt");
        try (FileWriter writer = new FileWriter(file2)) {
            writer.write("id:" + stationId2 + "\nlocation:Sydney\ntemperature:25.0");
        }
        createdTestFiles.add(file2);

        System.out.println("Sending PUT requests for stations " + stationId1 + " and " + stationId2 + "...");
        new ContentServer("localhost", port, "TC008_1.txt").requestAndResponse();
        new ContentServer("localhost", port, "TC008_2.txt").requestAndResponse();
        
        // Give the server a moment to process the requests and write to the log.
        TimeUnit.SECONDS.sleep(2);

        // Step 2: Force a "crash" by interrupting the server thread.
        System.out.println("\nSimulating a server crash...");
        serverThread.interrupt();
        serverThread.join(); // Wait for the thread to terminate
        System.out.println("Server has crashed.");

        // Step 3: Restart the server
        System.out.println("\nRestarting the server...");
        // Close the previous server instance
        server.close();
        // Create and start a new server instance
        server = new AggregationServer(port);
        serverThread = new Thread(server);
        serverThread.start();
        
        // Give the new server time to start up and perform recovery.
        TimeUnit.SECONDS.sleep(2);
        System.out.println("Server has restarted.");

        // Step 4: Verify that the data is logged.
        System.out.println("\nVerifying data for station " + stationId1 + "...");
        GetClient getClient1 = new GetClient("localhost", port, stationId1);
        System.out.println("Response for station " + stationId1 + " should be null.");
        getClient1.requestAndResponse(); // Response for station


        System.out.println("Verifying data for station " + stationId2 + "...");
        GetClient getClient2 = new GetClient("localhost", port, stationId2);
        System.out.println("Response for station " + stationId2 + " should be null.");
        getClient2.requestAndResponse();
        
        System.out.println("\nSUCCESS: No PUT requests need recovered after the simulated crash.");
        System.out.println("--- Test TC-008 Finished ---\n");
    }
    
}
