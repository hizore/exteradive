package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSlider.SpeedSlider;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SpeedIconDrawable;

final class VoiceSpeedButton extends ImageView {
    private final SpeedIconDrawable icon = new SpeedIconDrawable(false);
    private final GradientDrawable pill = new GradientDrawable();
    private final RippleDrawable ripple;

    VoiceSpeedButton(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setScaleType(ScaleType.CENTER);
        setImageDrawable(icon);
        pill.setCornerRadius(dp(16));
        ripple = new RippleDrawable(ColorStateList.valueOf(Color.TRANSPARENT),
                new InsetDrawable(pill, dp(1), dp(9), dp(1), dp(9)), null);
        setBackground(ripple);
        setVisibility(GONE);
        if (fragment == null) return; // Style editor preview.

        setOnClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            if (!Md3Player.supportsSpokenMessage(mc.getPlayingMessageObject())) return;
            int tenths = Math.round(mc.getPlaybackSpeed(false) * 10);
            float next = tenths < 10 ? 1f : tenths < 15 ? 1.5f : tenths < 20 ? 2f : 1f;
            mc.setPlaybackSpeed(false, next);
        });
        setOnLongClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            MessageObject voice = mc.getPlayingMessageObject();
            if (!Md3Player.supportsSpokenMessage(voice) || fragment.getParentLayout() == null) return false;
            SpeedSlider slider = new SpeedSlider(context, resourcesProvider);
            slider.setLabel(getString(R.string.OEPlayerSpeed));
            slider.setSpeed(mc.getPlaybackSpeed(false), false);
            slider.setOnValueChange((value, finished) -> {
                if (finished && mc.getPlayingMessageObject() == voice) {
                    float speed = Math.round(slider.getSpeed(value) * 10) / 10f;
                    slider.setSpeed(speed, false);
                    mc.setPlaybackSpeed(false, speed);
                }
            });
            ItemOptions.makeOptions(fragment, this).addView(slider, LayoutHelper.createLinear(208, 48)).show();
            return true;
        });
        NotificationCenter.getGlobalInstance().listen(this, NotificationCenter.messagePlayingSpeedChanged, args -> {
            if (getVisibility() == VISIBLE) updateSpeed(isShown());
        });
    }

    void showForVoice(boolean voice) {
        setVisibility(voice ? VISIBLE : GONE);
        if (voice) updateSpeed(false);
    }

    void setPalette(PlayerColors colors) {
        pill.setColor(colors.secondaryContainer);
        icon.setColor(colors.onSecondaryContainer);
        ripple.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.onSecondaryContainer, 0x24)));
    }

    private void updateSpeed(boolean animated) {
        float speed = MediaController.getInstance().getPlaybackSpeed(false);
        icon.setValue(speed, animated);
        setContentDescription(getString(R.string.OEPlayerSpeed) + " " + SpeedIconDrawable.formatNumber(speed) + "×");
    }
}
