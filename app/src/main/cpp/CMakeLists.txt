cmake_minimum_required(VERSION 3.18)
project(sdrdec C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fno-exceptions -fno-rtti")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/ft8lib)

set(FT8_SOURCES
  ft8lib/ft8/constants.c ft8lib/ft8/crc.c ft8lib/ft8/decode.c ft8lib/ft8/encode.c ft8lib/ft8/ldpc.c
  ft8lib/ft8/message.c ft8lib/ft8/text.c
  ft8lib/fft/kiss_fft.c ft8lib/fft/kiss_fftr.c
  ft8lib/common/monitor.c
  sdrdec_ft8.c)

add_library(sdrdec SHARED ${FT8_SOURCES} sdrdec_jni.c olivia_jni.cpp)
target_link_libraries(sdrdec m log)

# FreeDV RADE V1 (réseaux de neurones de 4 à 5 Mo chacun) : seulement pour les téléphones 64 bits.
if(ANDROID_ABI STREQUAL "arm64-v8a")
  target_compile_definitions(sdrdec PRIVATE SDRDEC_RADE=1 HAVE_CONFIG_H=1)
  target_include_directories(sdrdec PRIVATE opus opus/dnn opus/celt opus/include rade rade2)
  target_sources(sdrdec PRIVATE
    sdrdec_rade.c
    rade/rade_rx.c rade/rade_dec.c rade/rade_dec_data.c rade/rade_dsp.c rade/rade_ofdm.c rade/rade_bpf.c rade/rade_acq.c
    opus/dnn/fargan.c opus/dnn/fargan_data.c opus/dnn/nnet.c opus/dnn/nnet_default.c opus/dnn/parse_lpcnet_weights.c
    rade2/rade_v2_ofdm.c rade2/rade_rx_v2.c rade2/rade_dec_v2.c rade2/rade_dec_v2_data.c rade2/rade_sync.c rade2/rade_sync_data.c
    opus/dnn/lpcnet_tables.c)
endif()

# Essai de bouclage RADE V2 (si le dossier rade2test existe) : émetteur V2 + essai, jamais dans l application finale
if(ANDROID_ABI STREQUAL "arm64-v8a" AND EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/rade2test/rade_enc_v2_data.c)
  target_include_directories(sdrdec PRIVATE rade2test)
  target_sources(sdrdec PRIVATE rade2test/rade_enc_v2.c rade2test/rade_enc_v2_data.c rade2test/rade_tx_v2.c rade2test/sdrdec_rade_test.c)
endif()
