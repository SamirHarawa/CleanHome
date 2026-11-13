package com.samir.cleanhome;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.provider.Settings;

/**
 * CleanHome's own interface sounds (assets/sounds): a soft tick when focus moves, a rounder click on OK,
 * and a muted bump when focus can't go further. Quieter and shorter than the TV's system beeps.
 * Respects the system "sound effects" switch; falls back to the system sounds until ours have loaded.
 */
final class Sounds {
    private final Context c;
    private final AudioManager am;
    private final SoundPool pool;
    private final int tick, click, bump;
    private final boolean[] loaded = new boolean[3];
    private boolean enabled = true;

    Sounds(Context c) {
        this.c = c;
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        tick = load("tick");
        click = load("click");
        bump = load("bump");
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            if (id == tick) loaded[0] = true;
            else if (id == click) loaded[1] = true;
            else if (id == bump) loaded[2] = true;
        });
        refresh();
    }

    private int load(String name) {
        try (AssetFileDescriptor fd = c.getAssets().openFd("sounds/" + name + ".wav")) {
            return pool.load(fd, 1);
        } catch (Exception e) { return 0; }
    }

    /** Re-reads the system "sound effects" switch (call when Home comes to the front). */
    void refresh() {
        enabled = Settings.System.getInt(c.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
    }

    void nav(int systemFx) { play(0, tick, .55f, systemFx); }
    void click() { play(1, click, .6f, AudioManager.FX_KEY_CLICK); }
    void bump() { play(2, bump, .6f, -1); }

    private void play(int slot, int id, float vol, int systemFx) {
        if (!enabled) return;
        if (loaded[slot]) pool.play(id, vol, vol, 1, 0, 1f);
        else if (systemFx >= 0) am.playSoundEffect(systemFx);
    }

    void release() { pool.release(); }
}
