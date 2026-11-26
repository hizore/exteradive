package app.exteraless.player;

import android.content.Context;
import android.os.Bundle;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.LaunchActivity;

import app.exteraless.appearance.AppearanceConfig;

public final class Md3Player {

    private Md3Player() {
    }

    public static boolean enabled() {
        return AppearanceConfig.md3Player();
    }

    public static boolean miniEnabled() {
        return AppearanceConfig.md3MiniPlayer();
    }

    public static boolean supportsMiniPlayer(MessageObject message) {
        return message != null && message.getId() != 0
                && (message.isMusic() || supportsSpokenMessage(message));
    }

    static boolean supportsSpokenMessage(MessageObject message) {
        return message != null && (message.isVoice() && !message.isVoiceOnce()
                || message.isRoundVideo() && !message.isRoundOnce());
    }

    static String miniTitle(MessageObject message) {
        return message.isMusic() ? message.getMusicTitle() : message.getMusicAuthor();
    }

    static String miniSubtitle(MessageObject message) {
        if (message.isMusic()) return message.getMusicAuthor();
        return LocaleController.getString(message.isRoundVideo() ? R.string.AttachRound : R.string.AttachAudio);
    }

    public static void closePlayer() {
        MediaController mc = MediaController.getInstance();
        if (miniEnabled()) {
            mc.clearMusicPlaylistState();
        }
        mc.cleanupPlayer(true, true);
    }

    private static boolean handles(MessageObject messageObject) {
        return enabled() && messageObject != null && messageObject.isMusic();
    }

    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (handles(MediaController.getInstance().getPlayingMessageObject())) {
            if (PlayerSheet.instance != null) {
                PlayerSheet.instance.dismissImmediately();
            }
            return new PlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static void open(BaseFragment fragment, PlayerMiniView source) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (!supportsMiniPlayer(messageObject)) {
            return;
        }
        if (messageObject.isRoundVideo()) {
            if (fragment instanceof ChatActivity && fragment.getCurrentAccount() == messageObject.currentAccount
                    && ((ChatActivity) fragment).getDialogId() == messageObject.getDialogId()
                    && (((ChatActivity) fragment).getTopicId() == 0 || ((ChatActivity) fragment).getTopicId() == messageObject.getTopicId())) {
                ((ChatActivity) fragment).scrollToMessageId(messageObject.getId(), 0, false, 0, true, 0);
            } else if (fragment.getParentActivity() instanceof LaunchActivity) {
                PlayerActions.showInChat((LaunchActivity) fragment.getParentActivity(), messageObject);
            }
            return;
        }
        BottomSheet sheet = create(fragment.getParentActivity(), fragment.getResourceProvider());
        if (sheet instanceof PlayerSheet && source != null && source.canTransition()) {
            ((PlayerSheet) sheet).setTransitionSource(source);
        }
        fragment.showDialog(sheet);
    }

    public static boolean hidesContextPlayer(BaseFragment fragment, MessageObject messageObject) {
        if (!miniEnabled() || fragment == null || !supportsMiniPlayer(messageObject)) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }
}
