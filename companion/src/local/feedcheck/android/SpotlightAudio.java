package local.feedcheck.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

final class SpotlightAudio {
    private static final String RESTORE_MEDIA = "restore_media_mute";
    private final AudioManager audio;
    private final SharedPreferences state;

    SpotlightAudio(Context context) {
        audio = context.getSystemService(AudioManager.class);
        state = FeedCheckService.state(context);
    }

    void update(boolean spotlightCovered) {
        if (!spotlightCovered) {
            restore();
            return;
        }
        if (audio == null || audio.isVolumeFixed() || isMuted()) {
            return;
        }
        // Save the responsibility to restore before making a system-wide change.
        if (!ownsMute() && !state.edit().putBoolean(RESTORE_MEDIA, true).commit()) {
            return;
        }
        adjust(AudioManager.ADJUST_MUTE);
        if (!isMuted()) {
            state.edit().remove(RESTORE_MEDIA).apply();
        }
    }

    void restore() {
        if (audio == null || !ownsMute()) {
            return;
        }
        if (isMuted()) {
            adjust(AudioManager.ADJUST_UNMUTE);
        }
        if (!isMuted()) {
            state.edit().remove(RESTORE_MEDIA).apply();
        }
    }

    boolean isMuted() {
        return audio != null && audio.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    boolean ownsMute() {
        return state.getBoolean(RESTORE_MEDIA, false);
    }

    private void adjust(int direction) {
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        } catch (SecurityException ignored) {
            // Some devices restrict global media changes. Keep the visual blocker available.
        }
    }
}
