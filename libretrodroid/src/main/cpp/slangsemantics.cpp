/*
 *     Copyright (C) 2026  Mohammad Syuhada
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

#include "slangsemantics.h"

#include <cstring>

namespace libretrodroid {

namespace {

// "<prefix><number>" -> the number.
std::optional<unsigned> numbered(const std::string& name, const char* prefix) {
    size_t length = std::strlen(prefix);
    if (name.size() <= length || name.compare(0, length, prefix) != 0) return std::nullopt;
    unsigned value = 0;
    for (size_t i = length; i < name.size(); ++i) {
        if (name[i] < '0' || name[i] > '9' || value > 100000) return std::nullopt;
        value = value * 10 + (unsigned) (name[i] - '0');
    }
    return value;
}

} // namespace

std::optional<SlangTextureRef> slangTextureFor(const std::string& name, size_t pass,
                                               const std::vector<std::string>& aliases,
                                               const std::vector<std::string>& lutIds) {
    size_t count = aliases.size();
    // This frame's output exists only for an earlier pass.
    auto output = [&](size_t index) -> std::optional<SlangTextureRef> {
        if (index >= count) return std::nullopt;
        return SlangTextureRef{ index < pass ? SlangTextureKind::Output : SlangTextureKind::Feedback, (unsigned) index };
    };
    auto feedback = [&](size_t index) -> std::optional<SlangTextureRef> {
        if (index >= count) return std::nullopt;
        return SlangTextureRef{ SlangTextureKind::Feedback, (unsigned) index };
    };

    if (name == "Original") return SlangTextureRef{ SlangTextureKind::Original, 0 };
    if (name == "Source") return SlangTextureRef{ SlangTextureKind::Source, 0 };
    if (auto n = numbered(name, "OriginalHistory")) {
        if (*n == 0) return SlangTextureRef{ SlangTextureKind::Original, 0 };
        // A preset asking for hundreds of frames of history would be hundreds of full-size textures.
        if (*n > 64) return std::nullopt;
        return SlangTextureRef{ SlangTextureKind::History, *n };
    }
    if (auto n = numbered(name, "PassOutput")) return output(*n);
    if (auto n = numbered(name, "PassFeedback")) return feedback(*n);
    for (size_t i = 0; i < lutIds.size(); ++i) {
        if (name == lutIds[i]) return SlangTextureRef{ SlangTextureKind::Lut, (unsigned) i };
    }
    for (size_t i = 0; i < count; ++i) {
        if (aliases[i].empty()) continue;
        if (name == aliases[i]) return output(i);
        if (name == aliases[i] + "Feedback") return feedback(i);
    }
    return std::nullopt;
}

}
