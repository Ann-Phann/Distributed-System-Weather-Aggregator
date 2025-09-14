package com.weather.server.helper;

import java.io.IOException;
import java.net.Socket;
import java.io.ObjectOutputStream;

import com.weather.http.Response;

public class ResponseSender {
    /*
     * the data is sent directly back to the original client that made the request.
     */
    public static void sendResponse(Socket clientSocket, Response response) throws IOException {
        /*
         * serialization: send Java object
         * @param: clientSocket: the socket that we will send back to the client.
         * @param: response: response object to send to the client
         */
        
        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())){

            out.writeObject(response);
            out.flush();
        }
    } 


}
