JAVAC = javac
JAVA = java
SRC_DIR = src
BIN_DIR = bin

SOURCES = $(SRC_DIR)/*.java
PORT = 8080

.PHONY: all clean web

all:
	mkdir -p $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) $(SOURCES)

# build then launch the web UI at http://127.0.0.1:$(PORT)/
web: all
	$(JAVA) -cp $(BIN_DIR) MonteCarloMini.WebServer $(PORT)

clean:
	rm -rf $(BIN_DIR)
