package org.telegram.messenger;

/**
 * Переключатель дизайна: новый (liquid glass) против старого.
 *
 * Мастер-флаг — это штатный {@link LiteMode#FLAG_LIQUID_GLASS}. Каждый
 * видимый элемент интерфейса проверяется отдельно, чтобы можно было оставить
 * стекло там, где оно нравится, и вернуть обычный фон там, где не нравится.
 *
 * Переключение применяется при пересоздании Activity: решение «есть ли стекло»
 * принимается в момент создания фонового drawable, поэтому на лету флаги
 * не подхватываются.
 */
public class LiquidGlass {

    public static final int MAIN_TABS = 1;
    public static final int ACTION_BAR = 1 << 1;
    public static final int INPUT = 1 << 2;
    public static final int ATTACH = 1 << 3;
    public static final int SEARCH = 1 << 4;
    public static final int SHEET = 1 << 5;

    public static final int ALL = MAIN_TABS | ACTION_BAR | INPUT | ATTACH | SEARCH | SHEET;

    private static final String PREF_ELEMENTS = "dgGlassElements";

    private static int elements = -1;

    public static boolean isMasterEnabled() {
        return LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    private static Boolean masterCached;

    /**
     * Кэшированная проверка мастер-флага.
     *
     * Нужна там, где флаг читается на каждом кадре (например, в Theme.getColor):
     * там нельзя каждый раз доходить до LiteMode.getValue(), который ходит в
     * BatteryManager. Кэш сбрасывается в LiteMode.setAllFlags — единственном
     * месте, где значение флагов меняется.
     */
    public static boolean isMasterEnabledCached() {
        if (masterCached == null) {
            masterCached = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
        }
        return masterCached;
    }

    public static void invalidateCache() {
        masterCached = null;
    }

    public static int getElements() {
        if (elements == -1) {
            elements = SharedPreferencesHolder.load(PREF_ELEMENTS, ALL);
        }
        return elements;
    }

    public static void setElementEnabled(int element, boolean enabled) {
        int current = getElements();
        int updated = enabled ? (current | element) : (current & ~element);
        if (updated != current) {
            SharedPreferencesHolder.save(PREF_ELEMENTS, updated);
            elements = updated;
        }
    }

    public static boolean isElementEnabled(int element) {
        return (getElements() & element) != 0;
    }

    /**
     * Мастер выключен — стекло выключено везде, независимо от флагов элементов.
     *
     * Элемент {@link #ALL} или {@code 0} означает «конкретный элемент не задан» —
     * тогда действует только мастер-переключатель. Иначе маска элементов наследовалась бы
     * из провайдера, который элемент не объявляет, и отключение одного элемента
     * не отключало бы остальные.
     */
    public static boolean isOn(int element) {
        if (!isMasterEnabled()) {
            return false;
        }
        if (element == 0 || element == ALL) {
            return true;
        }
        return isElementEnabled(element);
    }

    private static class SharedPreferencesHolder {
        static android.content.SharedPreferences preferences() {
            return MessagesController.getGlobalMainSettings();
        }

        static int load(String key, int def) {
            return preferences().getInt(key, def);
        }

        static void save(String key, int value) {
            preferences().edit().putInt(key, value).apply();
        }
    }
}