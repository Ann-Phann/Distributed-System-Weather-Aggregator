package com.weather.server.helper;

import java.io.IOException;
import java.net.Socket;
import java.io.ObjectOutputStream;

import com.weather.http.Response;

public class ResponseSender {
    /*
     * the data is sent directly back to the original client that made the request.
     * 
     * Example format:
     *  HTTP/1.1 200 OK
        Content-Type: application/json
        Content-Length: 53

        {"location":"London","temperature":22.5,"wind_speed":15}
     */
    public static void sendResponse(Socket clientSocket, Response response) throws IOException {
        /*
         * True: the auto-flush feature. It ensures that every time call println(), the data is immediately sent over the network to the client. 
         * Without this, the data might remain buffered on the server side, and the client would not receive the response.
         * 
         * The PrintWriter is created on the clientSocket, so its output goes over the network to the specific machine that connected to your server.
         */
        // try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)){
        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())){
            // Write the response status line
            // out.println("HTTP/1.1 " + response.getStatusCode() + " " + response.getStatusMessage());
            
            // // Write the headers
            // for (String header : response.getHeaders().keySet()) {
            //     out.println(header + ": " + response.getHeaders().get(header));
            // }
            // out.println(); // Blank line between headers and body

            // // Write the body if it exists
            // if (response.getBody() != null) {
            //     out.println(response.getBody());
            // }

            out.writeObject(response);
            out.flush();
        }
    } 


}
