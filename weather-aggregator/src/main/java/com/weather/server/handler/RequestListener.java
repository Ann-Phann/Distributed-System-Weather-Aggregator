package com.weather.server.handler;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.PriorityBlockingQueue;

import com.weather.http.Request;
import com.weather.http.Response;
import com.weather.http.StatusCode;
import com.weather.server.AggregationServer;
import com.weather.server.helper.RequestNode;
import com.weather.server.helper.RequestParser;
import com.weather.server.helper.ResponseSender;
import com.weather.server.helper.Storage;

public class RequestListener implements Runnable {
    private final AggregationServer server;
    private final ServerSocket serverSocket;
    private final PriorityBlockingQueue<RequestNode> requestQueue;
    private Storage storage;

    public RequestListener(AggregationServer server, ServerSocket serverSocket, PriorityBlockingQueue<RequestNode> requestQueue, Storage storage) {
        this.server = server;
        this.serverSocket = serverSocket;
        this.requestQueue = requestQueue;
        this.storage = storage;
    }

    // public RequestListener(ServerSocket serverSocket, PriorityBlockingQueue<RequestNode> requestQueue) {
    //     this.serverSocket = serverSocket;
    //     this.requestQueue = requestQueue;
    // }

    @Override
    public void run() {
        System.out.println("RequestListener is running and listening for client connections.");
        try {
            // Set a timeout to prevent the thread from blocking indefinitely on serverSocket.accept()
            this.serverSocket.setSoTimeout(1000); 
            while (!Thread.currentThread().isInterrupted() && server.isRunning()) {
                Socket clientSocket = null;
                try {
                    // Accept a new client connection
                    clientSocket = serverSocket.accept(); // block + waiting for a connection
                    System.out.println("Client connected from: " + clientSocket.getInetAddress());

                    // Read the request from the client's input stream
                    // BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    // Request request = RequestParser.parse(in);
                    
                    ObjectInputStream objectIn = new ObjectInputStream(clientSocket.getInputStream());
                    Object obj = objectIn.readObject();

                    if (obj instanceof Request) {
                        Request request = (Request) obj; 

                        // Check for valid method 
                        switch (request.getMethod()) {
                            case "PUT":
                            case "GET":   
                                // Check for Lamport clock header and update the server clock
                                int clientLamportValue = 0;
                                String lamportHeader = request.getHeaders().get("Lamport-Clock");
                                if (lamportHeader != null) {
                                    clientLamportValue = Integer.parseInt(lamportHeader);
                                }

                                // update server clock and assign new clock value for RequestNode
                                int newClockValue = this.server.getClock().updateAndGet(clientLamportValue);

                                // add Storage logic for PUT request: record the request before put in the request queue
                                if ("PUT".equalsIgnoreCase(request.getMethod())) {
                                    String stationId = String.valueOf(request.getHeaders().get("Station-Id"));
                                    String jsonBody = request.getBody();
                                    this.storage.logPutRequest(String.valueOf(newClockValue), stationId, jsonBody);
                                }

                                // update Request object with new time 
                                request.getHeaders().put("Lamport-Clock", String.valueOf(newClockValue));

                                // Create a RequestNode with new timestamp and add it to the shared queue
                                RequestNode requestNode = new RequestNode(clientSocket, request, newClockValue);
                                requestQueue.put(requestNode); // this will block adding in if the queue is full
                                System.out.println("Request from " + clientSocket.getInetAddress() + " added to queue. Queue size: " + requestQueue.size());       
                                break;

                            default:
                                // If the method is not "PUT" or "GET", send a Bad Request response
                                Response response = new Response(StatusCode.BAD_REQUEST);
                                // ResponseSender.sendResponse(clientSocket, response);
                                System.err.println("Received invalid method from client. Sent 400 Bad Request.");
                                try (ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {
                                    oos.writeObject(response);
                                    oos.flush();
                                }

                                clientSocket.close();
                                break;
                        }
                            
                    } else {
                        // If the object is not a valid Request, send a Bad Request response
                        Response response = new Response(StatusCode.BAD_REQUEST);
                        // ResponseSender.sendResponse(clientSocket, response);
                        System.err.println("Received invalid object from client. Sent 400 Bad Request.");
                        try (ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {
                            oos.writeObject(response);
                            oos.flush();
                        }
                        clientSocket.close();
                    }
                    

                } catch (SocketTimeoutException e) {
                    // This is expected and allows the loop to check the thread's interruption status
                } catch (IOException | ClassNotFoundException | NumberFormatException e) {
                    System.err.println("Error accepting or processing client connection: " + e.getMessage());
                    // Close the socket on any error before the request is enqueued
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try {
                            clientSocket.close();
                        } catch (IOException ex) {
                            System.err.println("Error closing socket: " + ex.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    // System.err.println("ERROR: Invalid lamport clock value. Might be cannot parse newClockValue to String");
                    // e.printStackTrace();
                    Thread.currentThread().interrupt();
                    System.err.println("Listener thread was interrupted. Shutting down.");
                    return;
                } 
                // catch (ClassNotFoundException e) {
                //     System.err.println("Received an invalid object from the client.");
                //     e.printStackTrace();
                // }
            }
        } catch (IOException e) {
            System.err.println("Listener thread failed to initialize or experienced a fatal error: " + e.getMessage());
        } 
        
        finally {
            System.out.println("RequestListener thread shutting down.");
        }
        
        // finally {
        //     // cleanup the whole server
        //     try {
        //         if (serverSocket != null) {
        //             serverSocket.close();
        //         }
        //     } catch (IOException e) {
        //         System.err.println("Error closing server socket: " + e.getMessage());
        //     }
        // }
        System.out.println("RequestListener thread shutting down.");
    }
}
