# nx-mobile - netplay engine, link, DS link and shared TCP host tests. GPL-3.0-or-later.
# Run through `make -C libretrodroid/src/test/native/netplay test`.
CC ?= cc
OUT := out
SRC := ../../../main/cpp/netplay
CFLAGS := -std=c11 -Wall -Wextra -O1 -pthread -I$(SRC)
NET := $(SRC)/np_net.c $(SRC)/np_tcp.c
NET_H := $(SRC)/np_net.h $(SRC)/np_tcp.h
all: $(OUT)/test_engine $(OUT)/test_link $(OUT)/test_tcp $(OUT)/test_ndl
$(OUT):
	mkdir -p $(OUT)
$(OUT)/test_engine: test_engine.c $(SRC)/np_engine.c $(NET) $(SRC)/np_engine.h $(NET_H) | $(OUT)
	$(CC) $(CFLAGS) -o $@ test_engine.c $(SRC)/np_engine.c $(NET)
$(OUT)/test_link: test_link.c $(SRC)/np_link.c $(NET) $(SRC)/np_link.h $(NET_H) | $(OUT)
	$(CC) $(CFLAGS) -o $@ test_link.c $(SRC)/np_link.c $(NET)
$(OUT)/test_tcp: test_tcp.c $(NET) $(NET_H) | $(OUT)
	$(CC) $(CFLAGS) -o $@ test_tcp.c $(NET)
$(OUT)/test_ndl: test_ndl.c $(SRC)/ndl_link.c $(NET) $(SRC)/ndl_link.h $(NET_H) | $(OUT)
	$(CC) $(CFLAGS) -DNDL_TESTING -o $@ test_ndl.c $(SRC)/ndl_link.c $(NET)
test: all
	sh run.sh
clean:
	rm -rf $(OUT)
.PHONY: all test clean
