# ==============================================================================
# Makefile for the Weather Aggregation Project
#
# To use:
#   1. Make sure you are in the project's root directory.
#   2. Run 'make compile' to build the project.
#   3. Run 'make run-server' to start the aggregation server.
#   4. Run 'make run-content' to start a content server.
#   5. Run 'make run-client' to run a GET client.
#   6. Run 'make clean' to remove compiled files and logs.
# ==============================================================================

.PHONY: all compile run-server run-content run-client clean test

# Default target runs all 
all: clean compile

# Compile the project
compile: 
	@echo "Compiling project..."
	@mvn compile
	@echo "Compilation complete."

# Runs the Aggregation Server
PORT ?= 4567
run-server: compile
	@echo "Starting Aggregation Server on port $(PORT)..."
	@mvn exec:java -Dexec.mainClass="com.weather.server.AggregationServer" -Dexec.args="$(PORT)"

# Runs the Content Server
# Example: make run-content FILE=data2.txt
# Default to data1.txt
FILE ?= data1.txt
run-content: compile
	@echo "Starting Content Server..."
	@mvn exec:java -Dexec.mainClass="com.weather.client.contentserver.ContentServer" -Dexec.args="localhost:$(PORT) $(FILE)"


# Runs the GET Client
# Example: make run-client ID=ID001
# Defaults to getting all data if no ID is specified
ID ?= 
run-client: compile
	@echo "Starting GET Client..."
	@if [ -z "$(ID)" ]; then \
		echo "GET request for all station..."; \
		mvn exec:java -Dexec.mainClass="com.weather.client.getclient.GetClient" -Dexec.args="localhost:$(PORT)"; \
	else \
		echo "GET request for Station ID $(ID)..."; \
		mvn exec:java -Dexec.mainClass="com.weather.client.getclient.GetClient" -Dexec.args="localhost:$(PORT) $(ID)";\
	fi

# Cleans the project
clean:
	@echo "Cleaning up..."
	@mvn clean
	@rm -f data/server.log
	@echo "Cleanup complete."

# Runs tests (we will fill this in later)
test:
	@echo "Running tests..."
	@mvn test
	@echo "Test complete."
