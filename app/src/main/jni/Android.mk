LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := pziprar

UNRAR_SRC := rar strlist strfn pathfn smallfn global file filefn filcreat \
	archive arcread unicode system crypt crc rawread encname \
	resource match timefn rdwrfn consio options errhnd rarvm secpassword \
	rijndael getbits sha1 sha256 blake2s hash extinfo extract volume \
	list find unpack headers threadpool rs16 cmddata ui largepage \
	filestr scantree dll qopen

LOCAL_SRC_FILES := pzip_rar.cpp $(addprefix unrar/,$(addsuffix .cpp,$(UNRAR_SRC)))
LOCAL_C_INCLUDES := $(LOCAL_PATH)/unrar
LOCAL_CPPFLAGS := -std=c++17 -O2 -fexceptions -frtti -fvisibility=hidden \
	-DRARDLL -DSILENT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE \
	-Wno-logical-op-parentheses -Wno-switch -Wno-dangling-else -Wno-everything
LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
