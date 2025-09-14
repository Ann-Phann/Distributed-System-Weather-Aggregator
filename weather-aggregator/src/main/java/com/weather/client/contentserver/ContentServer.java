package com.weather.client.contentserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.client.AbstractClient;
import com.weather.http.Request;
import com.weather.http.Response;

public class ContentServer extends AbstractClient {
    public String id;
    String dir = "src/main/java/com/weather/client/contentserver/data";
    private final String fileName;

    public ContentServer(String hostname, int port, String fileName) {
        super(hostname, port);
        this.fileName = fileName;
    }

    @Override
    protected Request createRequest() {
        String jsonBody = "";

        try {
            // get the weather data
            jsonBody = this.readFileGetData();
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: Fail to load data, File not found");
            return null;
        } catch (JsonProcessingException e ) {
            System.err.println("ERROR: Failed to process JSON data." + e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            return null;
        }
        // Return null if jsonBody is empty due to a previous error
        if (jsonBody == null || jsonBody.isEmpty()) {
            return null;
        }

        // build the request 
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Content-Length", String.valueOf(jsonBody.getBytes().length));
        headers.put("Station-Id", this.id);

        // build the request 
        Request request = new Request("PUT", "/weather/" + this.id, jsonBody, headers);

        return request;
    }

    @Override
    protected void showResponse(Response response) {

        int status = response.getStatusCode();
        String body = response.getBody();
        HashMap<String, String> headers = response.getHeaders();

        System.out.println("--- Content Server Response --- ");
        System.out.println("Status: " + status);

        System.out.println("Headers:");
        for (String key : headers.keySet()) {
            System.out.println("\t" + key + ": " + headers.get(key));
        }

        System.out.println("Body: " + body);

        System.out.println("----------------");

        // check for success code
        if (status == 200 || status == 201) {
            System.out.println("Success: Data uploaded successfully.");

        } else {
            System.err.println("ERROR: Failed to upload data");
        }

    }

    // intepret the file
    public String readFileGetData() throws FileNotFoundException, JsonProcessingException {
        if (this.fileName == null || fileName.isEmpty()){
            System.err.println("Missing file path data");
            return "";
        } 
         
        HashMap<String, String> weatherData = new HashMap<>();
        File file = new File(dir + "/" + fileName);

        try (Scanner reader = new Scanner(file)){
            while (reader.hasNextLine()) {
                String line = reader.nextLine();
                String[] parts = line.split(":", 2); 
                
                // ensure the line is key-pair value
                if (parts.length == 2) {
                    // remove leading/trailing whitespace
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    weatherData.put(key, value);
                }
            }

            if (weatherData.containsKey("id")) {
                this.id = weatherData.get("id");
            } else {
                throw new IllegalArgumentException("Input missing id");
            }
            
        }

        ObjectMapper objectMapper = new ObjectMapper();

        // serialisation: send JSON String
        return objectMapper.writeValueAsString(weatherData);
    }

    /*
     * Get Station ID from the file read in
     * 
     */
    public String getStationId() {
        return id;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Not appropriate command line input. Follow this usage: ContentServer <hostname:port> <filepath>");
            return;
        }

        try {
            String[] path = args[0].split(":", 2);
            String hostname = path[0];
            int port = Integer.parseInt(path[1]);

            String fileName = args[1];

            // read the file: get the station id and weather data
            ContentServer contentServer = new ContentServer(hostname, port, fileName);
            
            // run the content server client
            contentServer.requestAndResponse(); 

        } catch (NumberFormatException e) {
            System.err.println("ERROR: Invalid port number. Please enter a valid integer.");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
