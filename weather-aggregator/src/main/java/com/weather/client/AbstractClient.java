package com.weather.client;

import com.weather.clock.LamportClock;
import com.weather.http.Request;
import com.weather.http.Response;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public abstract class AbstractClient {
    protected String hostname;
    protected int port;
    protected Socket socket;
    protected ObjectOutputStream outputStream; // the output stream to send data to the server
    protected ObjectInputStream inputStream; // the input stream to receive data from the server
    protected LamportClock clock; // logical clock
    
    
    // constructor
    public AbstractClient(String hostname, int port){
        // do initialise the Lamport: not included
        this.hostname = hostname;
        this.port = port;
        clock = new LamportClock();
    }


    /*     core methods      */
    /*
     * handle establishing the socket connection to the server.
     * can include retry mechanism 
     */
    public void connect() throws IOException, InterruptedException {
        final int MAX_TRY = 3;

        for (int retry = 0; retry < MAX_TRY; retry++) {
            try {
                this.socket = new Socket(hostname, port);
                System.out.println("Successfully connected to " + this.hostname + ":" + this.port);
                return;

            } catch (IOException e) {
                if (retry < MAX_TRY - 1) {
                    System.out.println("Connection failed. Retrying in 5s");
                    Thread.sleep(5000);
                } else {
                    // last retry fail 
                    System.err.println("Cannot connect to " + this.hostname + ":" + this.port);
                    throw e;
                }
            }
        }
    }

    /*
     * this will orchestrate the entire communication. it will call the createRequest(), send the request then call showResponse() to handle server's reply.
     * retry mechanism: will try to send the request 3 times before failing
     */
    public void requestAndResponse() throws ClassNotFoundException {
        final int MAX_ATTEMPTS = 3;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
            connect(); // connect to the server

            // initialise streams
            // create output stream first
            outputStream = new ObjectOutputStream(socket.getOutputStream());

            // Create and send request
            Request request = createRequest();
            sendRequest(request);

            // create input stream to wait for response
            inputStream = new ObjectInputStream(socket.getInputStream());
            // wait for and show the response
            Response response = getResponse();
            showResponse(response);

            return; // cause to here everything is completed

            } catch (SocketTimeoutException | SocketException e) {
                // This handles both connection timeouts and a closed socket, which indicate a network failure or server crash.
                System.err.println("Communication error (attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS + "): " + e.getMessage());
                if (attempt < MAX_ATTEMPTS - 1) {
                    System.out.println("Retrying request...");
                    // Clean up and prepare for the next attempt
                    stop(); 
                } else {
                    System.err.println("Failed to get a response after " + MAX_ATTEMPTS + " attempts. Aborting.");
                    e.printStackTrace();
                    break;
                }
            
            } catch (IOException | InterruptedException e) {
                System.err.println("ERROR: An error occur during communication: " + e.getMessage());
                e.printStackTrace();
                break;
            } finally {
                stop();
            }
        }
    }
        
    

    /*
     * A helper method to send a pre-constructed Request object over the socket.
     * will have Lamport clock logic
     */
    protected void sendRequest(Request request) throws IOException {
        try {
            clock.increment();

            // add newly incremented clock val to header of request
            request.getHeaders().put("Lamport-Clock", String.valueOf(clock.get()));

            // serialise: to be sent over the network to server
            outputStream.writeObject(request);
            outputStream.flush();
            System.out.println("Sent request at logical time: " + clock.get());

        } catch (Exception e) {
            System.err.println("Cannot send Request: " + e);
        }
        
    }

    /*
     * A helper method to read a Response from the server's input stream. This is where the client will update its Lamport clock based on the timestamp received from the server.
     */
    protected Response getResponse() throws IOException, ClassNotFoundException {
        // wait for and read the response object from the server
        Response response = (Response) inputStream.readObject();

        String lamportHeader = response.getHeaders().get("Lamport-Clock");
        if (lamportHeader != null) {
            // get the server's Lamport Clock from the response header
            int receivedClock = Integer.parseInt(lamportHeader);
            // update the local clock to match 
            clock.update(receivedClock);
            System.out.println("Received response. Update clock to: " + clock.get());

        } else {
            System.err.println("ERROR: Received response without a Lamport clock header. Local clock not updated.");
        }
        return response;
    }

    /*
     * A method to gracefully close the socket connection and clean up resources.
     */
    public void stop(){
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // abstract method
    /*
     * This method will be implemented by GETClient and ContentServer to create their specific types of requests (a GET request for GETClient, a PUT request with a JSON body for ContentServer).
     */
    protected abstract Request createRequest();

    /*
     * This method will handle displaying the data. The GETClient will print the weather data, while the ContentServer will print the success message.
     */
    protected abstract void showResponse(Response response);
}
