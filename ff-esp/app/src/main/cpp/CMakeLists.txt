cmake_minimum_required(VERSION 3.22.1)
project(memread)
add_library(memread SHARED memread.cpp)
target_link_libraries(memread log)
