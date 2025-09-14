package com.weather.server.helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.weather.server.AggregationServer;

/*
 * write-ahead log for persistent  storage.
 * handle the file I/O and the logic for crash recovery, using text file
 * Only store PUT request because if the GET client dont need to be stored its request. 
 * Because if server crash the GET client will recognise this as communication fail and resend
 */
public class Storage {
    private static final String LOG_FILE_PATH = "data/server.log";
    private AggregationServer server;
    private Semaphore fileLock;

    public Storage(AggregationServer server, Semaphore fileLock) {
        this.server = server;
        this.fileLock = fileLock;

        new File("data").mkdirs(); //ensure the data directory exists

    }

    /*
     * Method to log new PUT request
     * param uniqueId: this is the Lamport-Clock which we is unique on each request
     */
    public void logPutRequest(String uniqueId, String stationId, String jsonBody) throws IOException, InterruptedException {
        // fileLock.acquire();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
            writer.write("PUT:" + uniqueId + ":" + stationId + ":" + jsonBody);
            writer.newLine();
            writer.flush(); // explitcitly flsuh the stream
        } 
        // finally {
        //     fileLock.release();
        // }
    }

    public void logCompletion(String uniqueId) throws IOException, InterruptedException {
        // fileLock.acquire();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
            writer.write("COMMIT:" + uniqueId);
            writer.newLine();
            writer.flush();

        } 
        // finally {
        //     fileLock.release();
        // }
    }

    /*
     * Method to load data and perform crash recovery on restart
     */
    public synchronized void loadAndRecover() throws IOException, InterruptedException {
        File logFile = new File(LOG_FILE_PATH);
        if (!logFile.exists() || logFile.length() == 0) {
            System.out.println("No log file found or log is empty. Starting fresh");
            return;
        }

        System.out.println("Log file found. Beginning crash recovery");
        ConcurrentHashMap<String, String> incompleteRequests = new ConcurrentHashMap<>();

        fileLock.acquire();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PUT:")) {
                    String[] parts = line.split(":", 4);

                    // this is the PUT log
                    if (parts.length != 4) {
                        System.err.println("ERROR: Wrong PUT log request format for line: " + line);
                        continue;
                    }

                    String uniqueId = parts[1];
                    // String stationId = parts[2];
                    String jsonBody = parts[3];
                    incompleteRequests.put(uniqueId, jsonBody);

                } else if (line.startsWith("COMMIT:")) {
                    String[] parts = line.split(":", 2);
                    String uniqueId = parts[1];
                    incompleteRequests.remove(uniqueId); // remove if find the matching
                }
            }
        } finally {
            fileLock.release();
        }

        if (incompleteRequests.isEmpty()) {
            System.out.println("Recovery complete. No incomplete requests found.");
        } else {
            System.out.println("Found " + incompleteRequests.size() + " incomplete requests. Re-processing...");
            for (String uniqueId : incompleteRequests.keySet()) {
                String bodyOfIncompleteRequest = incompleteRequests.get(uniqueId);
                server.getWeatherData().put(uniqueId, new ExpirableData(bodyOfIncompleteRequest));

                // Re-logging the completion to ensure consistency after recovery
                logCompletion(uniqueId);
                System.out.println("Recovered and re-processed data for station " + uniqueId);
            }
            System.out.println("Recovery complete. All data is consistent.");
        }
    }

}
