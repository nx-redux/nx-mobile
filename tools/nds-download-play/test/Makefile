# Host tests for DS Download Play: the core's protocol code (from the patched melonDS DS tree, which
# `gmake -C cores melondsds` checks out) and the client's pure parts.
CORE = ../../../cores/src/melondsds/src/libretro/net
CXXFLAGS = -std=c++20 -Wall -Wextra -O1 -g -I$(CORE) -I../source
SOURCES = wmb_test.cpp $(CORE)/wmb.cpp

test: wmb_test
	./wmb_test

wmb_test: $(SOURCES) $(wildcard $(CORE)/*.hpp) $(wildcard $(CORE)/*.h) $(wildcard ../source/*.h)
	$(CXX) $(CXXFLAGS) -o $@ $(SOURCES)

clean:
	rm -f wmb_test

.PHONY: test clean
