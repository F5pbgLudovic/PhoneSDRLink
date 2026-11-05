cmake_minimum_required(VERSION 3.18)
project(sdrdec C)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/ft8lib)

set(FT8_SOURCES
  ft8lib/ft8/constants.c ft8lib/ft8/crc.c ft8lib/ft8/decode.c ft8lib/ft8/encode.c ft8lib/ft8/ldpc.c
  ft8lib/ft8/message.c ft8lib/ft8/text.c
  ft8lib/fft/kiss_fft.c ft8lib/fft/kiss_fftr.c
  ft8lib/common/monitor.c
  sdrdec_ft8.c)

add_library(sdrdec SHARED ${FT8_SOURCES} sdrdec_jni.c)
target_link_libraries(sdrdec m log)

# FreeDV RADE V1 (réseaux de neurones de 4 à 5 Mo chacun) : seulement pour les téléphones 64 bits.
if(ANDROID_ABI STREQUAL "arm64-v8a")
  target_compile_definitions(sdrdec PRIVATE SDRDEC_RADE=1 HAVE_CONFIG_H=1)
  target_include_directories(sdrdec PRIVATE opus opus/dnn opus/celt opus/include rade)
  target_sources(sdrdec PRIVATE
    sdrdec_rade.c
    rade/rade_rx.c rade/rade_dec.c rade/rade_dec_data.c rade/rade_dsp.c rade/rade_ofdm.c rade/rade_bpf.c rade/rade_acq.c
    opus/dnn/fargan.c opus/dnn/fargan_data.c opus/dnn/nnet.c opus/dnn/nnet_default.c opus/dnn/parse_lpcnet_weights.c
    opus/dnn/lpcnet_tables.c)
endif()
