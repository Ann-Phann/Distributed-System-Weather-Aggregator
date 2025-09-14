package com.weather.server;

import com.weather.clock.LamportClock;
import com.weather.server.handler.DataExpirer;
import com.weather.server.handler.RequestHandler;
import com.weather.server.handler.RequestListener;
import com.weather.server.helper.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * Primary job is to act as listener and dispatcher. It sits in a loop, waiting for new client connections to arrive.
 * Delegate request to new thread for processing.
 * 
 * AggregationServer class implements Runnable to allow the server's main loop to run in its own, dedicated thread.
 */
public class AggregationServer implements Runnable {
    private volatile boolean isRunning = true;
    private int port; // default = 4567 
    private PriorityBlockingQueue<RequestNode> requestQueue; // store both PUT and GET request
    private LamportClock clock;
    private ConcurrentHashMap<String, ExpirableData> weatherData; 
    private ServerSocket serverSocket;
    private final Semaphore fileLock;

    
    private Storage storage; // for persistence storage
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public AggregationServer(int port) {
        this.port = port;
        this.requestQueue = new PriorityBlockingQueue<RequestNode>(11, Comparator.comparingLong(request -> request.getLamportClockValue()));
        this.clock = new LamportClock();
        this.weatherData = new ConcurrentHashMap<>();
        this.fileLock = new Semaphore(1, true); // ensure only 1 thread writing to the Storage
        this.storage = new Storage(this, this.fileLock);
    }

    @Override
    public void run() {
        System.out.println("Starting Aggregation Server on port " + this.port + "...");

        try {
            this.serverSocket = new ServerSocket(this.port);

            // perform crash recovery on startup
            this.storage.loadAndRecover();

            // Schedule the data expiration task to run every 15 seconds
            scheduler.scheduleAtFixedRate(new DataExpirer(weatherData, fileLock), 15, 15, TimeUnit.SECONDS);
            
            // Start Producer thread (Listener)
            // RequestListener listener = new RequestListener(this, serverSocket, requestQueue);
            RequestListener listener = new RequestListener(this, serverSocket, requestQueue, storage);
            new Thread(listener).start();

            // start the Consumer thread.
            Thread consumerThread = new Thread(() -> {
            try {
                // The main loop for the consumer thread
                while (this.isRunning) {
                    // Take a RequestNode from the queue when available. This call blocks until an item is available.
                    RequestNode requestNode = requestQueue.take();
                    // Pass the RequestNode to a handler to process it.
                    // This creates a temporary object to handle the request logic.
                    RequestHandler handler = new RequestHandler(requestNode, this, storage);
                    handler.run();
                }
            } catch (InterruptedException e) {
                // Handle thread interruption
                Thread.currentThread().interrupt();
            }
        });
        consumerThread.start();

        /* Add this for Testing: If running bit by bit we dont need this */
        while (isRunning) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isRunning = false; // Exit the loop if interrupted
            }
        }

        } catch (IOException e) {
            System.err.println("Could not listen on port " + this.port);
            System.exit(-1);
        } catch (Exception e) {
            System.err.println("ERROR: when running Aggregation Server: "+ e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            this.isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                scheduler.shutdown();
            }

            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: An error occurred while closing the server socket.");
            e.printStackTrace();
        }
    }

    // getter and setter 
    public ConcurrentHashMap<String, ExpirableData> getWeatherData() {
        return weatherData;
    }

    public Semaphore getFileLock() {
        return fileLock;
    }

    public LamportClock getClock () {
        return clock;
    }

    public Storage getStorage() {
        return storage;
    }

    public boolean isRunning() {
        return isRunning;
    }
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("ERROR: Invalid numbers of arguments. Usage: <port number>");
            return;
        }

        int port = 4567; // default
        try {
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Invalid port number format. Please enter a valid integer.");
                    return; // Exit if the format is wrong
                }
            } 
            AggregationServer aggregationServer = new AggregationServer(port);
            Thread server = new Thread(aggregationServer);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down gracefully...");
                aggregationServer.close();
                try {
                    server.join(); // Wait for the main server thread, the one that run AS to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        } catch (NumberFormatException e) {
            System.err.println("ERROR: Invalid port number format. Please enter a valid integer.");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during server startup: " + e.getMessage());
            e.printStackTrace();
        }
        
    }

}
