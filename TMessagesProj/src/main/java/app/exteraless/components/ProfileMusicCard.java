package app.exteraless.components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileMusicView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.HashSet;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.player.PlayerColors;

public class ProfileMusicCard extends FrameLayout implements Theme.Colorable {

    private static final long MUSIC_EMOJI_ID = 5271627010681108586L;

    private static final float[] PATTERN = {
        -5.5f, 20f, 20f, 0.35f,
        -5.5f, -20f, 20f, 0.35f,
        -36f, -42f, 22f, 0.375f,
        -36f, 0f, 25f, 0.425f,
        -36f, 42f, 22f, 0.375f,
        -70f, 22f, 23f, 0.35f,
        -70f, -22f, 23f, 0.35f,
        -99f, 46f, 21f, 0.275f,
        -99f, 0f, 22f, 0.325f,
        -99f, -46f, 21f, 0.275f,
        -128f, -23f, 20f, 0.225f,
        -128f, 23f, 20f, 0.225f
    };

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout cardLayout;
    private final BackupImageView imageView;
    private final TextView labelView;
    private final TextView nameView;
    private final TextView artistView;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
    private final GradientDrawable background;

    private Integer coverSeed;
    private boolean loadingCover;
    private PlayerColors palette;
    private int paletteSeed;
    private long currentEmojiId = -1;
    private long currentDocumentId = -1;
    private long imageToken;
    private long paletteToken = -1;
    private Runnable onCardClick;
    private Runnable onCardLongClick;

    public ProfileMusicCard(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setPadding(dp(12), 0, dp(12), 0);

        background = new GradientDrawable();
        background.setDither(true);
        background.setOrientation(LocaleController.isRTL ? GradientDrawable.Orientation.RIGHT_LEFT : GradientDrawable.Orientation.LEFT_RIGHT);
        background.setCornerRadius(dpf2(AppearanceConfig.sectionRadius()));

        cardLayout = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                emoji.setColor(palette.primary);
                drawPattern(canvas, emoji, getWidth(), getHeight(), .40f);
                super.dispatchDraw(canvas);
            }
        };
        cardLayout.setBackground(background);
        cardLayout.setClipToOutline(true);
        cardLayout.setOutlineProvider(ViewOutlineProviderImpl.fromDrawable(background));
        cardLayout.setClickable(true);
        cardLayout.setOnClickListener(v -> {
            if (onCardClick != null) {
                onCardClick.run();
            }
        });
        cardLayout.setOnLongClickListener(v -> {
            if (onCardLongClick == null) {
                return false;
            }
            onCardLongClick.run();
            return true;
        });
        ScaleStateListAnimator.apply(cardLayout, 0.035f, 1.5f);
        addView(cardLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(cardLayout, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 12, 12, 12, 12));

        imageView = new BackupImageView(context);
        imageView.setClipToOutline(true);
        imageView.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, coverCornerRadius()));
        imageView.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                if (loadingCover && set && !thumb) {
                    extractColors(imageReceiver.getBitmap());
                }
            }
        });
        row.addView(imageView, LayoutHelper.createLinear(68, 68, 0, 0, 12, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        labelView = new TextView(context);
        labelView.setText(LocaleController.getString(R.string.AccDescrProfileMusic));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        labelView.setTypeface(AndroidUtilities.bold());
        labelView.setLetterSpacing(.04f);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(labelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        NotificationCenter.listenEmojiLoading(nameView);
        texts.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        NotificationCenter.listenEmojiLoading(artistView);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        updateColors();
    }

    public void setOnCardClickListener(Runnable listener) {
        onCardClick = listener;
    }

    public void setOnCardLongClickListener(Runnable listener) {
        onCardLongClick = listener;
    }

    public void set(TLRPC.Document document, long emojiDocumentId) {
        if (document == null) {
            return;
        }
        CharSequence title = ProfileMusicView.getTitle(document);
        CharSequence author = ProfileMusicView.getAuthor(document);
        if (TextUtils.isEmpty(title)) {
            title = LocaleController.getString(R.string.AudioUnknownTitle);
        }
        if (TextUtils.isEmpty(author)) {
            author = LocaleController.getString(R.string.AudioUnknownArtist);
        }
        nameView.setText(Emoji.replaceEmoji(title, nameView.getPaint().getFontMetricsInt(), false));
        artistView.setText(Emoji.replaceEmoji(author, artistView.getPaint().getFontMetricsInt(), false));

        long emojiId = emojiDocumentId != 0 ? emojiDocumentId : MUSIC_EMOJI_ID;
        if (emojiId != currentEmojiId) {
            currentEmojiId = emojiId;
            emoji.set(emojiId, true);
        }

        if (document.id != currentDocumentId) {
            currentDocumentId = document.id;
            applyCover(document);
        }
    }

    private void applyCover(TLRPC.Document document) {
        imageToken++;
        coverSeed = null;
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 1000);
        ImageLocation location = thumb != null ? ImageLocation.getForDocument(thumb, document) : null;
        String artworkUrl = location == null ? MessageObject.getArtworkUrl(document, false) : null;
        loadingCover = location != null || !TextUtils.isEmpty(artworkUrl);
        if (location != null) {
            imageView.setImage(location, null, (Drawable) null, 0, document);
        } else if (!TextUtils.isEmpty(artworkUrl)) {
            imageView.setImage(ImageLocation.getForPath(artworkUrl), "300_300", (Drawable) null, 0, null);
        } else {
            imageView.setImageResource(R.drawable.nocover_big, getThemedColor(Theme.key_player_button));
        }
        updateColors();
    }

    private void extractColors(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || paletteToken == imageToken) {
            return;
        }
        final Bitmap sample = PlayerColors.sample(bitmap);
        if (sample == null) return;
        final long token = paletteToken = imageToken;
        final int fallback = PlayerColors.fallbackSeed(resourcesProvider);
        Utilities.globalQueue.postRunnable(() -> {
            final int seed = PlayerColors.seedFromSample(sample, fallback);
            sample.recycle();
            AndroidUtilities.runOnUIThread(() -> {
                if (token != imageToken) return;
                coverSeed = seed;
                updateColors();
            });
        });
    }

    @Override
    public void updateColors() {
        final int seed = coverSeed != null ? coverSeed : PlayerColors.fallbackSeed(resourcesProvider);
        final boolean dark = PlayerColors.isDark(resourcesProvider);
        if (palette != null && paletteSeed == seed && palette.dark == dark) return;
        paletteSeed = seed;
        palette = PlayerColors.fromSeed(seed, dark);
        background.setColors(new int[]{palette.surfaceHigh, palette.primaryContainer});
        background.setStroke(dp(1), ColorUtils.setAlphaComponent(palette.outlineVariant, 0x99));
        labelView.setTextColor(palette.primary);
        nameView.setTextColor(palette.onSurface);
        artistView.setTextColor(palette.onSurfaceVariant);
        cardLayout.invalidate();
    }

    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(Theme.key_featuredStickers_addButton);
        colors.add(Theme.key_windowBackgroundWhite);
    }

    private float coverCornerRadius() {
        return dpf2(Math.max(AppearanceConfig.sectionRadius() - 12, 8));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private static void drawPattern(Canvas canvas, Drawable pattern, float w, float h, float alpha) {
        if (alpha <= 0) {
            return;
        }
        for (int i = 0; i < PATTERN.length; i += 4) {
            final float x = dpf2(PATTERN[i]) + w;
            final float y = dpf2(PATTERN[i + 1]) + h / 2f;
            final float size = dpf2(PATTERN[i + 2]) / 2f;
            pattern.setBounds((int) (x - size), (int) (y - size), (int) (x + size), (int) (y + size));
            pattern.setAlpha((int) (255 * alpha * PATTERN[i + 3]));
            pattern.draw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        emoji.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        emoji.detach();
    }
}
