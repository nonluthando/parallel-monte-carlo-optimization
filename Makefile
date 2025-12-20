JAVAC = javac
JAVA = java
SRC_DIR = src
BIN_DIR = bin

SOURCES = $(SRC_DIR)/*.java

.PHONY: all clean

all:
	mkdir -p $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) $(SOURCES)

clean:
	rm -rf $(BIN_DIR)
