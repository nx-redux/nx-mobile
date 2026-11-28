# Host tests for DS Download Play: the core's protocol code (from the patched melonDS DS tree, which
# `gmake -C cores melondsds` checks out) and the client's pure parts.
CORE = ../../../cores/src/melondsds/src/libretro/net
CXXFLAGS = -std=c++20 -Wall -Wextra -O1 -g -I$(CORE) -I../source -DCORE_DIR='"$(CORE)"'
SOURCES = wmb_test.cpp mailbox_test.cpp ui_test.cpp $(CORE)/wmb.cpp
CSOURCES = ../source/ui.c

test: wmb_test
	./wmb_test

wmb_test: $(SOURCES) $(CSOURCES) $(wildcard $(CORE)/*.hpp) $(wildcard $(CORE)/*.h) $(wildcard ../source/*.h)
	$(CC) -std=c11 -Wall -Wextra -O1 -g -I../source -c -o ui.o $(CSOURCES)
	$(CXX) $(CXXFLAGS) -o $@ $(SOURCES) ui.o

clean:
	rm -f wmb_test ui.o

.PHONY: test clean
