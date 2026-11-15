package com.samir.cleanhome;

import android.content.Context;
import android.media.AudioManager;

/**
 * Interface sounds: the TV's own, the same as everywhere else on Google TV. Moving focus plays the system's
 * navigation sound and OK its click (Android skips both when "sound effects" is off). Hitting the end of a
 * row is silent, as it is on Google TV; the card's little nudge says it instead.
 */
final class Sounds {
    private final AudioManager am;

    Sounds(Context c) { am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }

    /** Nothing to re-read: the system sounds follow the "sound effects" switch by themselves. */
    void refresh() { }

    void nav(int systemFx) { if (systemFx >= 0) am.playSoundEffect(systemFx); }
    void click() { am.playSoundEffect(AudioManager.FX_KEY_CLICK); }
    void bump() { }

    void release() { }
}
