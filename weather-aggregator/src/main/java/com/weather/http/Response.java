package com.weather.http;

import java.io.Serializable;
import java.util.HashMap;

public class Response implements Serializable {
    private int statusCode;
    private String statusMessage;
    private String body; // JSON
    private HashMap<String, String> headers;

    public Response(StatusCode code) {
        this.statusCode = code.getStatusCode();
        this.statusMessage = code.toString();
        this.headers = new HashMap<>();
    }

    public HashMap<String, String> getHeaders() { return headers; }

    public void addHeaders(String key, String value) {
        headers.put(key, value);
    }

    public int getStatusCode() { return statusCode; }

    public void setStatus(int status) {
        this.statusCode = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getBody() { return body; }

    public void setBody(String body) {
        this.body = body;
    }

}
