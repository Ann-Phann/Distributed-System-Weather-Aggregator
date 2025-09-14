# Distributed-System-Assignment2
This project leverage Maven to set up the folders structure. 

## Main Components
### Aggregation Server
A central hub whose responsible is to responding to requests and make changes to the weather data store in concurrent hash map.

### Content Server
Content server provides weather data via text file to the aggregation server.

### Get Client
Get Client retrieve weather data from aggregation server. It can choose to get data from specific station ID or all.

## Main Functionality
- Smooth interaction and data exchange between the server and clients.
- Ability to manage multiple concurrent PUT and GET requests.
- Lamport Clock ensures events are ordered in strictly increasing logical time.
- Message handling fully aligned with HTTP standards, including support for status codes 200, 400, 404, and 500.
- Automatic check for outdated content after 30 seconds.
- Clients attempt to reconnect if the server is unavailable during the initial connection or while sending a request.
- The server maintains a log of all PUT requests, recording when it comes in and whether they were processed, to ensure data persistence.

## Design Explanation
The system is designed as a distributed, fault-tolerant weather data aggregation service. It consists of three main components: **Content Servers**, which upload weather data; an **Aggregation Server**, which acts as a central data hub; and **GET Clients**, which retrieve this data. The core of our design focuses on ensuring concurrent operations are handled safely and that data is persistent and recoverable in the event of a server crash.

---

### Multi-threaded Architecture
The Aggregation Server's design is based on the **producer-consumer pattern** to handle multiple concurrent client requests efficiently. A **`RequestListener` thread** acts as the producer, constantly listening for new client connections and adding incoming requests to a shared queue. A separate consumer thread, calling the **`RequestHandler`**, continuously processes these requests by taking them from the queue one by one.

To manage the order of requests in a distributed environment, we use a **Lamport Clock**. Each request is tagged with a logical timestamp, and the server's queue is a **`PriorityBlockingQueue`** that automatically orders requests based on this clock value. This approach ensures a globally consistent ordering of events without relying on a central physical clock.

---

### Data Persistence and Fault Tolerance
Data persistence is handled by a **write-ahead log**. Instead of relying solely on an in-memory data store, every `PUT` request is first appended to a log file on disk. The system uses a **two-phase commit** protocol: a `PUT` entry is logged first, followed by a `COMMIT` entry once the transaction is complete. This log serves as the single source of truth for the server's data state.

In the event of an unexpected server crash, the system can perform a **crash recovery** on restart. The server reads the log file and rebuilds its in-memory data store by re-processing any logged `PUT` requests that do not have a corresponding `COMMIT` entry. This guarantees that no data is lost and the server's state remains consistent even after an abrupt failure.

To ensure **thread safety** during critical file I/O operations, a `Semaphore` is used to protect the write-ahead log. This prevents multiple threads from attempting to write to the log file simultaneously, avoiding race conditions and ensuring data integrity.

## How to run manually
I have made Makefile which we can compile and run the components effectively and gracefully.

### How to compile
Before doing any of these command line below, run this command line to direct to correct folder
```
cd weather-aggregator
```

Run this command to compile every files
```
make compile
```

### Run manually
To run **server**
```
# Using default port: 4567
make run-server

# Using different port
make run-server PORT=<port>
```

Open a new terminal to run **content server**
```
# Without explitly say which data file: data1.txt default
make run-content

# Explicitly data file name
make run-content FILE=<data_name>

# Example:
make run-content FILE=data2.txt
```

Open a new terminal to run **Get Client**
```
# To get all stationId key
make run-client 

# To get specific stationId's weather data 
make run-client ID=<stationId>

# Example:
make run-client ID=ID001
```

## Automated Testing
This include setting up the server before doing each test and cleaning up all temporary test files before moving to the next test,
The testing includes:
- Verify that a valid PUT request is processed correctly and the data is stored.
- Verify that the server can retrieve and return a specific station's weather data.
- Verify the server's ability to handle multiple simultaneous PUT requests without data corruption or deadlocks.
- Verify the server's stability when a mix of GET and PUT requests are processed concurrently.
- Verify the server correctly handles requests for station IDs that have no associated data.
- Verify that the server can recover from a crash and restore its data state from the write-ahead log.

### To run test
Before doing any of these command line below, run this command line to direct to correct folder
```
cd weather-aggregator
```

Then, run test file
```
mvn test
```