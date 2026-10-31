package com.zuobiao.browser;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 原生播放器：HLS / MP4 直链，手势 + 倍速 + 续播 + 画中画。 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView hud;
    private FrameLayout topBar;
    private TextView speedBtn;

    private final float[] rateValues = {1f, 1.25f, 1.5f, 2f, 0.75f};
    private int rateIndex = 0;

    private String mediaKey;
    private PlaybackStore store;
    private final Handler saver = new Handler(Looper.getMainLooper());

    private float downX, downY;
    private long downPos;
    private float downBrightness;
    private boolean horizontalSeek;
    private android.media.AudioManager audio;
    private int maxVol = 15;
    private int startVol;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");
        if (url == null) { finish(); return; }

        mediaKey = url;
        store = new PlaybackStore(this);
        PlaybackStore.Entry saved = store.get(url);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView);

        topBar = new FrameLayout(this);
        topBar.setBackgroundColor(0x66000000);
        TextView back = Ui.text(this, "←", 20, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundResource(android.R.drawable.list_selector_background);
        topBar.addView(back, new FrameLayout.LayoutParams(Ui.dp(this, 48), -1));

        TextView tv = Ui.text(this, title == null ? "播放" : title, 13, Color.WHITE);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(Ui.dp(this, 56), 0, Ui.dp(this, 140), 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(tv, new FrameLayout.LayoutParams(-1, -1));

        speedBtn = Ui.text(this, "1.0x", 12, Color.WHITE);
        speedBtn.setGravity(Gravity.CENTER);
        speedBtn.setBackgroundColor(0x33FFFFFF);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 32));
        slp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        slp.rightMargin = Ui.dp(this, 12);
        topBar.addView(speedBtn, slp);

        if (Build.VERSION.SDK_INT >= 26) {
            TextView pip = Ui.text(this, "◱", 16, Color.WHITE);
            pip.setGravity(Gravity.CENTER);
            pip.setBackgroundColor(0x33FFFFFF);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
            plp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            plp.rightMargin = Ui.dp(this, 74);
            topBar.addView(pip, plp);
            pip.setOnClickListener(v -> enterPip());
        }

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, Ui.dp(this, 44));
        tlp.gravity = Gravity.TOP;
        root.addView(topBar, tlp);

        hud = Ui.text(this, "", 14, Color.WHITE);
        hud.setGravity(Gravity.CENTER);
        hud.setBackgroundColor(0x77000000);
        hud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 48));
        hlp.gravity = Gravity.CENTER;
        root.addView(hud, hlp);

        setContentView(root);
        Ui.applyInsets(this);
        hideBars();
        audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            maxVol = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        }
        back.setOnClickListener(v -> finish());

        String ua = getIntent().getStringExtra("ua");
        String cookie = getIntent().getStringExtra("cookie");
        String referer = getIntent().getStringExtra("referer");

        Map<String, String> headers = new HashMap<>();
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
        if (cookie != null && !cookie.isEmpty()) headers.put("Cookie", cookie);
        if (ua == null || ua.isEmpty()) {
            ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36";
        }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this, http);
        DefaultMediaSourceFactory msf = new DefaultMediaSourceFactory(ds);

        player = new ExoPlayer.Builder(this).setMediaSourceFactory(msf).build();
        playerView.setPlayer(player);

        MediaItem.Builder mi = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8")) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (low.contains(".mpd")) mi.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (low.contains(".mp4") || low.contains(".m4v")) mi.setMimeType(MimeTypes.VIDEO_MP4);
        player.setMediaItem(mi.build());
        player.setHandleAudioBecomingNoisy(true);

        float speed = (saved != null && saved.speed > 0.1f && saved.speed <= 3f) ? saved.speed : 1f;
        player.setPlaybackSpeed(speed);
        applyRateLabel(speed);

        player.prepare();

        if (PlaybackStore.worthResume(saved)) {
            player.seekTo(saved.pos);
            player.play();
            showHud("已从 " + fmt(saved.pos) + " 继续");
        } else {
            player.play();
        }

        speedBtn.setOnClickListener(v -> {
            rateIndex = (rateIndex + 1) % rateValues.length;
            float s = rateValues[rateIndex];
            player.setPlaybackSpeed(s);
            applyRateLabel(s);
            store.put(mediaKey, player.getCurrentPosition(), player.getDuration(), s);
        });

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                showHud("播放失败：" + (error == null ? "" : error.getErrorCodeName()));
            }
        });

        playerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX(); downY = e.getY();
                        downPos = player.getCurrentPosition();
                        downBrightness = getWindow().getAttributes().screenBrightness;
                        if (downBrightness < 0) downBrightness = 0.5f;
                        if (audio != null) startVol = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                        horizontalSeek = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 24) {
                            horizontalSeek = true;
                            long dur = Math.max(1, player.getDuration());
                            long delta = (long) (dx / view.getWidth() * dur);
                            long target = Math.max(0, Math.min(dur, downPos + delta));
                            player.seekTo(target);
                            showHud(fmt(target) + " / " + fmt(dur));
                        } else if (Math.abs(dy) > 24) {
                            if (downX < view.getWidth() / 2f) {
                                float nb = Math.max(0.02f, Math.min(1f, downBrightness - dy / view.getHeight()));
                                WindowManager.LayoutParams lp = getWindow().getAttributes();
                                lp.screenBrightness = nb;
                                getWindow().setAttributes(lp);
                                showHud("亮度 " + Math.round(nb * 100) + "%");
                            } else {
                                if (audio != null) {
                                    int delta = (int) (-dy / view.getHeight() * maxVol);
                                    int nv = Math.max(0, Math.min(maxVol, startVol + delta));
                                    audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, nv, 0);
                                    showHud("音量 " + (maxVol == 0 ? 0 : nv * 100 / maxVol) + "%");
                                } else {
                                    showHud(dy < 0 ? "音量 +" : "音量 -");
                                }
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        hud.setVisibility(View.GONE);
                        return horizontalSeek;
                    default:
                        return false;
                }
            }
        });

        saver.postDelayed(new Runnable() {
            @Override public void run() {
                saveProgress();
                saver.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void applyRateLabel(float s) {
        for (int i = 0; i < rateValues.length; i++) {
            if (Math.abs(rateValues[i] - s) < 0.01f) rateIndex = i;
        }
        speedBtn.setText(String.format(Locale.ROOT, "%.2fx", s));
    }

    private void saveProgress() {
        if (player == null || player.getDuration() <= 0) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        if (pos < 1000) return;
        if (pos >= dur - 1000) {
            // 已经播完，不再续播
            store.remove(mediaKey);
            return;
        }
        store.put(mediaKey, pos, dur, player.getPlaybackParameters().speed);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
            b.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(b.build());
        } catch (Exception e) {
            Ui.toast(this, "无法进入画中画");
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= 26 && player != null && player.isPlaying()) enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (topBar != null) topBar.setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (hud != null && inPip) hud.setVisibility(View.GONE);
    }

    private void hideBars() {
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void showHud(String s) {
        hud.setText(s);
        hud.setVisibility(View.VISIBLE);
        hud.removeCallbacks(hudHide);
        hud.postDelayed(hudHide, 1200);
    }

    private final Runnable hudHide = () -> hud.setVisibility(View.GONE);

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    @Override
    protected void onStop() {
        saveProgress();
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        saver.removeCallbacksAndMessages(null);
        saveProgress();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    static Intent intent(Context c, String url, String title,
                         String referer, String ua, String cookie) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", title);
        i.putExtra("referer", referer);
        i.putExtra("ua", ua);
        i.putExtra("cookie", cookie);
        return i;
    }
}
