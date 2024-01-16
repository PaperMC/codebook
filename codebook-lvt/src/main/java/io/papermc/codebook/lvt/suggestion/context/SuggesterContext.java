package io.papermc.codebook.lvt.suggestion.context;

import dev.denwav.hypo.core.HypoContext;
import io.papermc.codebook.lvt.LvtTypeSuggester;

public record SuggesterContext(HypoContext hypoContext, LvtTypeSuggester typeSuggester) {
}
