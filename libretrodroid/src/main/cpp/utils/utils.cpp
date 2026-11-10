/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <iostream>
#include <fstream>
#include <stdexcept>
#include <memory>
#include <unistd.h>

#include "utils.h"
#include "../log.h"
#include "../errorcodes.h"
#include "libretrodroidexception.h"

namespace libretrodroid {

Utils::ReadResult Utils::readFileAsBytes(const std::string &filePath) {
    std::ifstream fileStream(filePath, std::ios::binary);
    if (!fileStream.is_open()) {
        LOGE("Cannot open %s for reading.", filePath.c_str());
        throw LibretroDroidError("Cannot open the game file", ERROR_LOAD_GAME);
    }
    fileStream.seekg(0, std::ios::end);
    std::streamoff end = fileStream.tellg();
    if (end < 0) {
        LOGE("Cannot get the size of %s.", filePath.c_str());
        throw LibretroDroidError("Cannot get the game file's size", ERROR_LOAD_GAME);
    }
    auto size = static_cast<size_t>(end);
    std::unique_ptr<char[]> bytes(new char[size]);
    fileStream.seekg(0, std::ios::beg);
    fileStream.read(bytes.get(), static_cast<std::streamsize>(size));
    if (static_cast<size_t>(fileStream.gcount()) != size) {
        LOGE("Short read on %s: expected %zu bytes but got %lld.", filePath.c_str(), size, (long long) fileStream.gcount());
        throw LibretroDroidError("Cannot read the whole game file", ERROR_LOAD_GAME);
    }

    return ReadResult { size, bytes.release() };
}

Utils::ReadResult Utils::readFileAsBytes(const int fileDescriptor) {
    FILE* file = fdopen(fileDescriptor, "r");
    if (file == nullptr) {
        LOGE("Cannot open file descriptor %d for reading.", fileDescriptor);
        ::close(fileDescriptor);   // ours either way (see utils.h)
        throw LibretroDroidError("Cannot open the game file", ERROR_LOAD_GAME);
    }

    // fdopen transfers ownership of the fd to the FILE*, so it is closed via fclose, on every path: close(fileDescriptor)
    // would close an fd owned by the FILE*, which fdsan treats as fatal.
    long end = -1;
    if (fseek(file, 0, SEEK_END) == 0) end = ftell(file);
    if (end < 0 || fseek(file, 0, SEEK_SET) != 0) {
        LOGE("Cannot get the size of file descriptor %d.", fileDescriptor);
        fclose(file);
        throw LibretroDroidError("Cannot get the game file's size", ERROR_LOAD_GAME);
    }
    auto size = static_cast<size_t>(end);

    std::unique_ptr<char[]> bytes(new char[size]);
    size_t bytesRead = fread(bytes.get(), sizeof(char), size, file);
    fclose(file);
    if (bytesRead != size) {
        LOGE("Short read on file descriptor %d: expected %zu bytes but got %zu.", fileDescriptor, size, bytesRead);
        throw LibretroDroidError("Cannot read the whole game file", ERROR_LOAD_GAME);
    }
    return ReadResult { size, bytes.release() };
}

size_t Utils::getFileSize(FILE* file) {
    fseek(file, 0, SEEK_SET);
    fseek(file, 0, SEEK_END);
    size_t size = ftell(file);
    fseek(file, 0, SEEK_SET);
    return size;
}

const char* Utils::cloneToCString(const std::string &input) {
    char* result = new char[input.length() + 1];
    std::strcpy(result, input.c_str());
    return result;
}

} //namespace libretrodroid