# Adding a Translation

Thanks for helping translate Neuro Karaoke! Adding a new language takes three steps.

## Step 1 — Copy the template

Copy `strings_template.xml` to the correct resource folder:

| Language | Folder |
|---|---|
| Japanese | `app/src/main/res/values-ja/strings.xml` |
| Korean | `app/src/main/res/values-ko/strings.xml` |
| German | `app/src/main/res/values-de/strings.xml` |
| French | `app/src/main/res/values-fr/strings.xml` |
| Finnish | `app/src/main/res/values-fi/strings.xml` |
| Ukrainian | `app/src/main/res/values-uk/strings.xml` |
| Russian | `app/src/main/res/values-ru/strings.xml` |
| Spanish | `app/src/main/res/values-es/strings.xml` |
| Portuguese | `app/src/main/res/values-pt/strings.xml` |
| Italian | `app/src/main/res/values-it/strings.xml` |
| Dutch | `app/src/main/res/values-nl/strings.xml` |
| Polish | `app/src/main/res/values-pl/strings.xml` |
| Traditional Chinese | `app/src/main/res/values-zh-TW/strings.xml` |

For other languages, use the standard [BCP-47](https://www.iana.org/assignments/language-subtag-registry) tag with `values-<tag>/`.

## Step 2 — Translate the strings

Open your new `strings.xml` and replace every English value with the translation.
Keep the `name` attribute exactly as-is — only the text between the tags changes.

```xml
<!-- Original -->
<string name="nav_tab_home">Home</string>

<!-- Japanese translation -->
<string name="nav_tab_home">ホーム</string>
```

### Rules

- **Do not translate** strings inside `<!-- Brand names (keep untranslated) -->` — leave those as English.
- **Do not change** format specifiers like `%1$s`, `%1$d`, `%2$d`. Keep them in the same position (the order can change for grammar, e.g. `%2$s and %1$s` is fine).
- **Escape** apostrophes: `it\'s`, not `it's`.
- **Escape** ampersands: `&amp;`, not `&`.
- For plurals, include all quantity forms your language needs (`zero`, `one`, `two`, `few`, `many`, `other`).

## Step 3 — Register the language

Open `app/src/main/java/com/neurokaraoke/data/repository/LocaleManager.kt` and add one line to `SUPPORTED_LOCALES`:

```kotlin
val SUPPORTED_LOCALES: List<SupportedLocale> = listOf(
    SupportedLocale("en",    Locale.ENGLISH,      "English"),
    SupportedLocale("zh-CN", Locale("zh", "CN"),  "简体中文"),
    SupportedLocale("ja",    Locale.JAPANESE,      "日本語"),   // ← add your language here
)
```

- `code` — the BCP-47 tag (must match the `values-<tag>` folder name, using `-r` for region: `zh-CN` → `values-zh-rCN`)
- `locale` — the Java `Locale` used for formatting
- `nativeName` — the language's own name as its speakers write it (shown in Settings)

That's all. The Settings screen and language switching pick it up automatically.

## Submitting

Open a PR with:
- `app/src/main/res/values-<tag>/strings.xml` (your translation)
- The one-line change to `LocaleManager.kt`

No other files need to change.
