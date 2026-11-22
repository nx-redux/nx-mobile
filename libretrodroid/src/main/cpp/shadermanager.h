/*
 *     Copyright (C) 2022  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_SHADERMANAGER_H
#define LIBRETRODROID_SHADERMANAGER_H

#include <string>
#include <unordered_map>
#include <vector>

namespace libretrodroid {

class ShaderManager {
public:
    struct Pass {
        std::string vertex;
        std::string fragment;
        bool linear;
        float scale;

        bool operator==(const ShaderManager::Pass &other) const;
    };

    struct Chain {
        std::vector<Pass> passes;
        bool linearTexture;

        bool operator==(const ShaderManager::Chain &other) const;
        bool operator!=(const ShaderManager::Chain &other) const;
    };

    enum class Type {
        SHADER_DEFAULT = 0,
        SHADER_SHARP = 3,
    };

    struct Config {
        Type type;
        std::unordered_map<std::string, std::string> params;

        inline bool operator==(const Config& other) {
            return type == other.type && params == other.params;
        }
    };

private:
    static const std::string defaultShaderVertex;

    static const std::string defaultShaderFragment;
    static const std::string defaultSharpFragment;

public:
    static Chain getShader(const Config& config);
};

}

#endif //LIBRETRODROID_SHADERMANAGER_H
