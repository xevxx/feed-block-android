package local.feedcheck.android;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class FeedProfile {
    enum SnapchatMarker {
        SPOTLIGHT("Spotlight", "spotlight_container"),
        DISCOVER_CARD("Discover card", "df_large_story"),
        CHAT_LIST("Chat message list", "chat_message_list"),
        CHAT_INPUT("Chat input", "chat_input_text_field");

        final String label;
        final String view;
        final int bit;

        SnapchatMarker(String label, String view) {
            this.label = label;
            this.view = view;
            this.bit = 1 << ordinal();
        }

        static int mask(Set<String> views) {
            int mask = 0;
            for (SnapchatMarker marker : values()) {
                if (views.contains(marker.view)) {
                    mask |= marker.bit;
                }
            }
            return mask;
        }
    }

    private static final List<String> SNAPCHAT_CHAT = Arrays.asList(
            SnapchatMarker.CHAT_LIST.view, SnapchatMarker.CHAT_INPUT.view);

    enum Shortcut {
        CHAT("ngs_chat_icon_container", R.string.go_to_chat, R.string.chat_unavailable),
        SEARCH("action_bar_search_edit_text", R.string.search_instagram, R.string.search_unavailable);

        final String view;
        final int label;
        final int unavailable;

        Shortcut(String view, int label, int unavailable) {
            this.view = view;
            this.label = label;
            this.unavailable = unavailable;
        }
    }

    static final FeedProfile SNAPCHAT = new FeedProfile("com.snapchat.android", "blocking",
            R.string.app_snapchat, R.string.snapchat_feeds, false,
            new Rule(R.string.spotlight_blocked, true, Shortcut.CHAT,
                    Collections.singletonList(SnapchatMarker.SPOTLIGHT.view), SNAPCHAT_CHAT),
            new Rule(R.string.discover_blocked, false, Shortcut.CHAT,
                    Collections.singletonList(SnapchatMarker.DISCOVER_CARD.view), SNAPCHAT_CHAT));

    static final FeedProfile YOUTUBE = new FeedProfile("com.google.android.youtube", "block_youtube_shorts",
            R.string.app_youtube, R.string.youtube_shorts, false,
            new Rule(R.string.youtube_shorts_blocked, true, null,
                    Arrays.asList("reel_watch_fragment_root", "reel_recycler"), Collections.emptyList()));

    static final String INSTAGRAM_REELS_PAGER = "clips_viewer_view_pager";
    static final Rule INSTAGRAM_MORE_REELS = new Rule(R.string.instagram_more_reels_blocked, true, null,
            Collections.singletonList(INSTAGRAM_REELS_PAGER), Collections.emptyList());

    static final FeedProfile INSTAGRAM = new FeedProfile("com.instagram.android", "block_instagram_reels",
            R.string.app_instagram, R.string.instagram_feeds, true,
            new Rule(R.string.instagram_reels_blocked, true, null,
                    Collections.singletonList(INSTAGRAM_REELS_PAGER), Collections.emptyList(), "clips_tab"),
            new Rule(R.string.instagram_explore_blocked, false, Shortcut.SEARCH,
                    Collections.singletonList("explore_action_bar"), Collections.emptyList()));

    static final List<FeedProfile> SUPPORTED = Collections.unmodifiableList(Arrays.asList(SNAPCHAT, INSTAGRAM, YOUTUBE));

    static final class Rule {
        final int heading;
        final boolean muteMedia;
        final Shortcut shortcut;
        private final List<String> required;
        private final List<String> excluded;
        private final String selectedView;

        Rule(int heading, boolean muteMedia, Shortcut shortcut, List<String> required, List<String> excluded) {
            this(heading, muteMedia, shortcut, required, excluded, null);
        }

        Rule(int heading, boolean muteMedia, Shortcut shortcut, List<String> required, List<String> excluded,
                String selectedView) {
            if (required.isEmpty()) {
                throw new IllegalArgumentException("A feed rule requires at least one view ID");
            }
            this.heading = heading;
            this.muteMedia = muteMedia;
            this.shortcut = shortcut;
            this.required = required;
            this.excluded = excluded;
            this.selectedView = selectedView;
        }

        boolean matches(Set<String> views) {
            return views.containsAll(required) && Collections.disjoint(views, excluded);
        }
    }

    final String packageName;
    final String preference;
    final int appName;
    final int coverage;
    final boolean requireViewBounds;
    private final Rule[] rules;
    private final Set<String> viewNames = new LinkedHashSet<>();

    private FeedProfile(String packageName, String preference, int appName, int coverage,
            boolean requireViewBounds, Rule... rules) {
        this.packageName = packageName;
        this.preference = preference;
        this.appName = appName;
        this.coverage = coverage;
        this.requireViewBounds = requireViewBounds;
        this.rules = rules;
        for (Rule rule : rules) {
            viewNames.addAll(rule.required);
            viewNames.addAll(rule.excluded);
        }
    }

    static FeedProfile forPackage(String packageName) {
        for (FeedProfile profile : SUPPORTED) {
            if (profile.packageName.equals(packageName)) {
                return profile;
            }
        }
        return null;
    }

    boolean enabled(SharedPreferences preferences) {
        return preferences.getBoolean(preference, false);
    }

    Set<String> sample(Predicate<String> isVisible) {
        Set<String> views = new HashSet<>();
        for (String name : viewNames) {
            if (isVisible.test(packageName + ":id/" + name)) {
                views.add(name);
            }
        }
        return views;
    }

    Rule match(Set<String> views, Predicate<String> isSelected) {
        for (Rule rule : rules) {
            if (rule.matches(views) && (rule.selectedView == null
                    || isSelected.test(packageName + ":id/" + rule.selectedView))) {
                return rule;
            }
        }
        return null;
    }
}
