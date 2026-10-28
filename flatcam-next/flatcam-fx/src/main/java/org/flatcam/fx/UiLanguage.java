package org.flatcam.fx;

/** UI language only: does not change numeric, machine-code or file-format locales. */
enum UiLanguage {
    PT_BR("pt-BR", "Português (Brasil)", "pt_BR"), EN("en", "English", "en");

    final String tag, label, catalog;
    UiLanguage(String tag, String label, String catalog) { this.tag = tag; this.label = label; this.catalog = catalog; }
    static UiLanguage fromTag(String value) {
        for (var language : values()) if (language.tag.equals(value)) return language;
        return PT_BR;
    }
    @Override public String toString() { return label; }
}
