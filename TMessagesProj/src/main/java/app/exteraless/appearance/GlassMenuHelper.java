package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.ScrimOptions;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;

/**
 * Матовое стекло под меню сообщения и панелью реакций.
 *
 * <p>Ключ — {@link AppearanceConfig#glassMessageMenu()} (дефолт true, как в exteraGram).
 * Стекло включается только когда блюр реально доступен: тот же гейт, что и в exteraGram —
 * {@code BlurredBackgroundProviderImpl.checkBlurEnabled} (SharedConfig.chatBlurEnabled плюс
 * серверные флаги disableBlurInLight/DarkTheme). Если блюр недоступен — вызывающий код
 * должен оставить обычный popup, поведение при этом ровно прежнее.
 */
public abstract class GlassMenuHelper {

    private GlassMenuHelper() {
    }

    public static boolean isEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        return AppearanceConfig.glassMessageMenu()
            && BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourcesProvider);
    }

    /**
     * Отличие от апстримного {@code BlurredBackgroundProviderImpl.scrimMenuBackground} —
     * обводки нет совсем.
     */
    private static BlurredBackgroundProviderBuilder scrimMenuGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> Theme.multAlpha(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), isDark ? 0.85f : 0.76f))
            .setStrokeColorTop(0, 0)
            .setStrokeColorBottom(0, 0)
            .setStrokeColorFull(0, 0)
            .setStrokeWidth(0.0f, 0.0f);
    }

    /**
     * exteradive: сильные блюры (панели приподнятого сообщения). Тонировка заметно
     * прозрачнее обычного стекла — иначе размытие под ней не видно совсем.
     */
    private static BlurredBackgroundProviderBuilder strongGlass(Theme.ResourcesProvider resourcesProvider) {
        return strongGlass(resourcesProvider, Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
    }

    /** Тонировка панелей в цвет сообщения (чужое — входящий бабл). */
    private static BlurredBackgroundProviderBuilder strongGlass(Theme.ResourcesProvider resourcesProvider, int baseColor) {
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> Theme.multAlpha(baseColor, isDark ? 0.54f : 0.48f))
            .setStrokeColorTop(0, 0)
            .setStrokeColorBottom(0, 0)
            .setStrokeColorFull(0, 0)
            .setStrokeWidth(0.0f, 0.0f);
    }

    /**
     * exteradive: единая морозная панель — размытый снимок экрана, тонировка в
     * цвет сообщения, hairline-кромка сверху для объёма и одинаковый радиус.
     * Все панели поднятого сообщения строятся только через неё, поэтому
     * выглядят согласованно и не зависят от системного тумблера блюра.
     * <p>
     * Цвет бабла в темах сам по себе полупрозрачный, поэтому он сначала
     * «раскладывается» поверх сплошного фона меню: иначе панель выходила
     * насквозь прозрачной, а не окрашенной.
     */
    public static SnapshotFrostDrawable frostPanel(View host, int baseColor, float radiusDp) {
        return frostPanel(host, baseColor, radiusDp, 1f);
    }

    /** One surface for both the main actions and the swipe-back page. */
    public static SnapshotFrostDrawable applyFrostMenu(ActionBarPopupWindow.ActionBarPopupWindowLayout popup, int tint) {
        SnapshotFrostDrawable frost = frostPanel(popup, tint, 12);
        popup.setHideOwnBackgroundDrawable(true);
        popup.setBackground(null);
        popup.setFrostBackground(frost);
        if (popup.getSwipeBack() != null) {
            popup.getSwipeBack().setForegroundOnGlass(true);
        }
        return frost;
    }

    public static SnapshotFrostDrawable frostPanel(View host, int baseColor, float radiusDp, float opacity) {
        final boolean dark = Theme.isCurrentThemeDark();
        final int flat = ColorUtils.compositeColors(baseColor,
            Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        float factor = Math.max(0f, Math.min(1f, opacity));
        final int tint = Theme.multAlpha(flat, (dark ? 0.9f : 0.87f) * factor);
        return new SnapshotFrostDrawable(host)
            .setCornerRadius(AndroidUtilities.dp(radiusDp))
            .setTintColor(tint)
            .setFallbackColor(flat)
            .setStroke(dark ? 0x1FFFFFFF : 0x14000000, AndroidUtilities.dpf2(0.8f));
    }

    /** Тот же мороз для «пилюли» реакций: радиус задаётся по форме при отрисовке. */
    public static SnapshotFrostDrawable frostPill(View host, int baseColor) {
        return frostPill(host, baseColor, 1f);
    }

    public static SnapshotFrostDrawable frostPill(View host, int baseColor, float opacity) {
        return frostPanel(host, baseColor, 0, opacity);
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider)
            .setShadowColor(0x26000000, 0)
            .setShadowLayer(AndroidUtilities.dpf2(4.0f), 0.0f, 0.0f)
            .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackgroundFill(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider)
            .setShadowColor(0, 0)
            .build();
    }

    public static BlurredBackgroundProvider strongMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return strongGlass(resourcesProvider)
            .setShadowColor(0x26000000, 0)
            .setShadowLayer(AndroidUtilities.dpf2(4.0f), 0.0f, 0.0f)
            .build();
    }

    public static BlurredBackgroundProvider strongMenuBackground(Theme.ResourcesProvider resourcesProvider, int baseColor) {
        return strongGlass(resourcesProvider, baseColor)
            .setShadowColor(0x26000000, 0)
            .setShadowLayer(AndroidUtilities.dpf2(4.0f), 0.0f, 0.0f)
            .build();
    }

    public static BlurredBackgroundProvider strongMenuBackgroundFill(Theme.ResourcesProvider resourcesProvider) {
        return strongGlass(resourcesProvider)
            .setShadowColor(0, 0)
            .build();
    }

    public static BlurredBackgroundProvider strongMenuBackgroundFill(Theme.ResourcesProvider resourcesProvider, int baseColor) {
        return strongGlass(resourcesProvider, baseColor)
            .setShadowColor(0, 0)
            .build();
    }

    public static BlurredBackgroundDrawable createStrongPanelBackground(BlurredBackgroundDrawableViewFactory factory,
                                                                         Theme.ResourcesProvider resourcesProvider,
                                                                         View view) {
        return createStrongPanelBackground(factory, resourcesProvider, view,
            Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
    }

    public static BlurredBackgroundDrawable createStrongPanelBackground(BlurredBackgroundDrawableViewFactory factory,
                                                                         Theme.ResourcesProvider resourcesProvider,
                                                                         View view,
                                                                         int baseColor) {
        return factory.create(view, true)
            .setColorProvider(strongMenuBackground(resourcesProvider, baseColor))
            .setRadius(AndroidUtilities.dp(12.0f))
            .setPadding(AndroidUtilities.dp(8.0f))
            .setHasPadding(true);
    }

    public static BlurredBackgroundDrawable createStrongFill(BlurredBackgroundDrawableViewFactory factory,
                                                             Theme.ResourcesProvider resourcesProvider,
                                                             View view) {
        return createStrongFill(factory, resourcesProvider, view,
            Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
    }

    public static BlurredBackgroundDrawable createStrongFill(BlurredBackgroundDrawableViewFactory factory,
                                                             Theme.ResourcesProvider resourcesProvider,
                                                             View view,
                                                             int baseColor) {
        return factory.create(view, true).setColorProvider(strongMenuBackgroundFill(resourcesProvider, baseColor));
    }

    public static BlurredBackgroundDrawable createPanelBackground(BlurredBackgroundDrawableViewFactory factory,
                                                                  Theme.ResourcesProvider resourcesProvider,
                                                                  View view) {
        return factory.create(view, true)
            .setColorProvider(scrimMenuBackground(resourcesProvider))
            .setRadius(AndroidUtilities.dp(12.0f))
            .setPadding(AndroidUtilities.dp(8.0f))
            .setHasPadding(true);
    }

    public static BlurredBackgroundDrawable createFill(BlurredBackgroundDrawableViewFactory factory,
                                                       Theme.ResourcesProvider resourcesProvider,
                                                       View view) {
        return factory.create(view, true).setColorProvider(scrimMenuBackgroundFill(resourcesProvider));
    }

    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect, float radius, int alpha) {
        draw(drawable, canvas, rect, radius, alpha, 0.0f, 0.0f);
    }

    /**
     * Смещение компенсируется сдвигом холста: сам drawable рисуется по целым
     * координатам, поэтому его двигают вместе с канвасом.
     */
    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect,
                            float radius, int alpha, float offsetX, float offsetY) {
        final boolean translate = offsetX != 0.0f || offsetY != 0.0f;
        if (translate) {
            canvas.save();
            canvas.translate(-offsetX, -offsetY);
        }
        drawable.setRadius(radius);
        drawable.setAlpha(alpha);
        drawable.setBounds(
            Math.round(rect.left + offsetX),
            Math.round(rect.top + offsetY),
            Math.round(rect.right + offsetX),
            Math.round(rect.bottom + offsetY));
        drawable.draw(canvas);
        if (translate) {
            canvas.restore();
        }
    }

    public static void applyToGaps(View view, int color) {
        applyToGaps(view, color, false);
    }

    /** Рекурсивно по всем {@code GapView} внутри меню. */
    public static void applyToGaps(View view, int color, boolean dividerVisible) {
        if (view instanceof ActionBarPopupWindow.GapView) {
            final ActionBarPopupWindow.GapView gapView = (ActionBarPopupWindow.GapView) view;
            gapView.setColor(color);
            gapView.setDividerVisible(dividerVisible);
        } else if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyToGaps(viewGroup.getChildAt(i), color, dividerVisible);
            }
        }
    }

    /**
     * Не перенесено: {@code popupLayout.getSwipeBack().setForegroundDrawable(createFill(...))} —
     * у нашего {@code PopupSwipeBackLayout} нет {@code setForegroundDrawable}. Подпанель
     * swipe-back остаётся непрозрачной, как в апстриме.
     */
    public static void applyToPopup(BlurredBackgroundDrawableViewFactory factory,
                                    Theme.ResourcesProvider resourcesProvider,
                                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        popupLayout.setBackground(createPanelBackground(factory, resourcesProvider, popupLayout));
    }

    public static void applyToReactions(BlurredBackgroundDrawableViewFactory factory,
                                        Theme.ResourcesProvider resourcesProvider,
                                        ReactionsContainerLayout reactionsContainerLayout) {
        reactionsContainerLayout.setGlassBackground(factory, scrimMenuBackgroundFill(resourcesProvider));
    }

    /**
     * Переиспользуемое меню переключают между стеклом
     * и обычным popup'ом на лету.
     */
    public static void applyToReusedMenu(BlurredBackgroundDrawableViewFactory factory,
                                         GlassSourceBitmap sourceBitmap,
                                         Theme.ResourcesProvider resourcesProvider,
                                         ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout,
                                         View rootView,
                                         boolean glass) {
        if (popupLayout == null) {
            return;
        }
        if (glass) {
            applyToPopup(factory, resourcesProvider, popupLayout);
            applyToGaps(popupLayout, separatorColor(true, resourcesProvider));
            captureBlur(sourceBitmap, factory, rootView);
        } else {
            final Drawable background = popupLayout.getResources().getDrawable(R.drawable.popup_fixed_alert4).mutate();
            popupLayout.setBackgroundDrawable(background);
            popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            applyToGaps(popupLayout, separatorColor(false, resourcesProvider), true);
        }
    }

    /** Снимок экрана под меню для источника блюра. */
    public static void captureBlur(final GlassSourceBitmap sourceBitmap,
                                   final BlurredBackgroundDrawableViewFactory factory,
                                   final View view) {
        ScrimOptions.makeGlobalBlurBitmaps((bitmapBg, bitmapOptions) -> {
            sourceBitmap.setBitmap(bitmapOptions);
            Blur3Utils.checkBitmapSourceMatrixScale(sourceBitmap, view);
            factory.invalidateAllLinkedViews();
        });
    }

    /**
     * Цвет «зазора» между группами пунктов.
     * На стекле сплошной разделитель заменяется еле заметной подложкой цвета текста.
     */
    public static int separatorColor(boolean glass, Theme.ResourcesProvider resourcesProvider) {
        if (!glass) {
            return Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider);
        }
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        return Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider),
            isDark ? 0.03f : 0.06f);
    }
}
