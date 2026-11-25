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

#ifndef LIBRETRODROID_SLANGSEMANTICS_H
#define LIBRETRODROID_SLANGSEMANTICS_H

#include <cstddef>
#include <optional>
#include <string>
#include <vector>

namespace libretrodroid {

// Which texture a slang sampler (or a "...Size" uniform) names. index is the history depth, the pass or the LUT.
enum class SlangTextureKind { Original, Source, History, Output, Feedback, Lut };

struct SlangTextureRef {
    SlangTextureKind kind = SlangTextureKind::Original;
    unsigned index = 0;

    bool operator==(const SlangTextureRef& o) const { return kind == o.kind && index == o.index; }
};

// The texture a slang name stands for in pass `pass` of a chain with these pass aliases (one per pass, empty for
// none) and LUT ids: Original, Source, OriginalHistoryN, PassOutputN, PassFeedbackN, <alias>, <alias>Feedback, <lut>.
// A pass cannot read the output of itself or a later pass this frame, so that reads last frame's (feedback).
// nullopt for a name that is none of them.
std::optional<SlangTextureRef> slangTextureFor(const std::string& name, size_t pass,
                                               const std::vector<std::string>& aliases,
                                               const std::vector<std::string>& lutIds);

}

#endif //LIBRETRODROID_SLANGSEMANTICS_H
