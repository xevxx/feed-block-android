package local.feedcheck.android;

import android.content.SharedPreferences;

enum BlockScreenIcon {
    CAT(R.string.block_screen_icon_cat, R.drawable.block_screen_cat),
    GOOSE(R.string.block_screen_icon_goose, R.drawable.block_screen_goose),
    FROG(R.string.block_screen_icon_frog, R.drawable.block_screen_frog),
    SHIELD(R.string.block_screen_icon_shield, R.drawable.feed_check_badge);

    private static final String PREFERENCE = "block_screen_icon";
    private static final String LEGACY_SHOW_CAT = "show_block_screen_cat";

    final int label;
    final int drawable;

    BlockScreenIcon(int label, int drawable) {
        this.label = label;
        this.drawable = drawable;
    }

    static BlockScreenIcon selected(SharedPreferences state) {
        String value = state.getString(PREFERENCE, null);
        for (BlockScreenIcon icon : values()) {
            if (icon.name().equals(value)) {
                return icon;
            }
        }
        return state.getBoolean(LEGACY_SHOW_CAT, true) ? CAT : SHIELD;
    }

    void save(SharedPreferences state) {
        state.edit().putString(PREFERENCE, name()).apply();
    }
}
