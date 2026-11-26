ifeq ($(strip $(DEVKITARM)),)
$(error "Please set DEVKITARM in your environment. export DEVKITARM=<path to>devkitARM")
endif
TOPDIR ?= $(CURDIR)
include $(DEVKITARM)/3ds_rules
TARGET		:=	download-play
BUILD		:=	build
SOURCES		:=	source
DATA		:=	data
INCLUDES	:=	include
APP_TITLE	:=	3DS Download Play
APP_DESCRIPTION	:=	Join a nearby Download Play game
APP_AUTHOR	:=	NX Redux
ARCH	:=	-march=armv6k -mtune=mpcore -mfloat-abi=hard -mtp=soft
CFLAGS	:=	-g -Wall -O2 -mword-relocations -ffunction-sections $(ARCH)
CFLAGS	+=	$(INCLUDE) -D__3DS__
ASFLAGS	:=	-g $(ARCH)
LDFLAGS	=	-specs=3dsx.specs -g $(ARCH) -Wl,-Map,$(notdir $*.map)
LIBS	:= -lcitro2d -lcitro3d -lctru -lm
LIBDIRS	:= $(CTRULIB)
ifneq ($(BUILD),$(notdir $(CURDIR)))
export OUTPUT	:=	$(CURDIR)/$(TARGET)
export TOPDIR	:=	$(CURDIR)
export VPATH	:=	$(foreach dir,$(SOURCES),$(CURDIR)/$(dir))
export DEPSDIR	:=	$(CURDIR)/$(BUILD)
CFILES		:=	$(foreach dir,$(SOURCES),$(notdir $(wildcard $(dir)/*.c)))
export LD	:=	$(CC)
export OFILES	:=	$(CFILES:.c=.o)
export INCLUDE	:=	$(foreach dir,$(LIBDIRS),-I$(dir)/include) -I$(CURDIR)/$(BUILD)
export LIBPATHS	:=	$(foreach dir,$(LIBDIRS),-L$(dir)/lib)
export _3DSXFLAGS = --smdh=$(CURDIR)/$(TARGET).smdh
.PHONY: $(BUILD) clean all
all: $(BUILD)
$(BUILD):
	@[ -d $@ ] || mkdir -p $@
	@$(MAKE) --no-print-directory -C $(BUILD) -f $(CURDIR)/Makefile
clean:
	@rm -fr $(BUILD) $(TARGET).3dsx $(TARGET).smdh $(TARGET).elf
else
DEPENDS	:=	$(OFILES:.o=.d)
$(OUTPUT).3dsx	:	$(OUTPUT).elf $(OUTPUT).smdh
$(OUTPUT).elf	:	$(OFILES)
-include $(DEPENDS)
endif
