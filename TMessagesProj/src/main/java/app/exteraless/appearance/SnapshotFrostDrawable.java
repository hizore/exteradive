package app.exteraless.appearance;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.lang.ref.WeakReference;

/**
 * exteradive: панель-«мороз» поверх размытого снимка экрана.
 * <p>
 * Всё рисуется обычным Canvas: снимок чата уменьшается, сильно размывается
 * {@code stackBlur} и вырезается по форме панели. Никаких render-node слоёв,
 * поэтому результат одинаков на любом устройстве и не зависит от системного
 * тумблера блюра. Снимок «стоит на месте» — панель показывает ровно тот
 * участок, что находится за ней.
 */
public final class SnapshotFrostDrawable extends Drawable {

    /** Снимок текущего экрана; обновляется при каждом открытии панелей. */
    private static WeakReference<Bitmap> shared = new WeakReference<>(null);

    public static void setSnapshot(@Nullable Bitmap bitmap) {
        Bitmap old = shared.get();
        shared = new WeakReference<>(bitmap);
        if (old != null && old != bitmap && !old.isRecycled()) {
            old.recycle();
        }
    }

    public static @Nullable Bitmap getSnapshot() {
        Bitmap b = shared.get();
        return b != null && !b.isRecycled() ? b : null;
    }

    /**
     * Сила блюра — 0..1. Ноль пропускает фильтр; при downscale=1 снимок
     * остаётся в исходном разрешении. Максимальный радиус — 1/10 короткой стороны.
     */
    public static @Nullable Bitmap captureAndBlur(@Nullable View root, int downscale, float blurStrength) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return null;
        }
        try {
            final int ds = Math.max(1, downscale);
            final int w = Math.max(1, root.getWidth() / ds);
            final int h = Math.max(1, root.getHeight() / ds);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1.0f / ds, 1.0f / ds);
            int sx = root.getScrollX();
            int sy = root.getScrollY();
            if (sx != 0 || sy != 0) {
                canvas.translate(-sx, -sy);
            }
            canvas.drawColor(org.telegram.ui.ActionBar.Theme.getColor(
                    org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
            root.draw(canvas);
            blurInPlace(bitmap, blurStrength);
            return bitmap;
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    public static @Nullable Bitmap captureAndBlur(@Nullable View root, int downscale) {
        return captureAndBlur(root, downscale, 1f);
    }

    /**
     * exteradive: снимок окна через PixelCopy — единственный надёжный способ
     * на реальном устройстве: программный view.draw() в software-canvas там
     * отдаёт пустые кадры (блюр «не работал» именно поэтому). Результат
     * приходит асинхронно в главный поток.
     */
    public interface BlurResult {
        void onResult(@Nullable Bitmap blurred);
    }

    public static void captureWindowAndBlur(@Nullable android.app.Activity activity, int downscale,
                                            float blurStrength, @NonNull BlurResult cb) {
        android.view.Window window = activity == null ? null : activity.getWindow();
        View decor = window == null ? null : window.peekDecorView();
        if (window == null || decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0
                || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            // PixelCopy.request(Window, ...) появился в API 26.
            cb.onResult(captureAndBlur(decor, downscale, blurStrength));
            return;
        }
        final int ds = Math.max(1, downscale);
        final Bitmap bitmap;
        try {
            // PixelCopy scales the window into the destination; no full-size intermediate needed.
            bitmap = Bitmap.createBitmap(Math.max(1, decor.getWidth() / ds),
                    Math.max(1, decor.getHeight() / ds), Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            cb.onResult(null);
            return;
        }
        try {
            android.view.PixelCopy.request(window, new Rect(0, 0, decor.getWidth(), decor.getHeight()), bitmap, result -> {
                if (result == android.view.PixelCopy.SUCCESS) {
                    try {
                        blurInPlace(bitmap, blurStrength);
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(bitmap));
                        return;
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                bitmap.recycle();
                AndroidUtilities.runOnUIThread(() -> cb.onResult(null));
            }, Utilities.themeQueue.getHandlerSync());
        } catch (Throwable t) {
            FileLog.e(t);
            bitmap.recycle();
            cb.onResult(null);
        }
    }

    private static void blurInPlace(Bitmap bitmap, float blurStrength) {
        float strength = Math.max(0f, Math.min(1f, blurStrength));
        if (strength > 0f) {
            int base = Math.max(4, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 10);
            Utilities.stackBlurBitmap(bitmap, Math.max(1, Math.round(base * strength * strength)));
        }
        bitmap.setHasAlpha(false);
    }

    private WeakReference<View> hostRef;

    private final Paint frostPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF boundsF = new RectF();
    private final Matrix canvasMatrix = new Matrix();
    private final Matrix shaderMatrix = new Matrix();
    private final Matrix tmpMatrix = new Matrix();
    private final float[] originPoint = new float[2];
    private final int[] screenLocation = new int[2];

    private BitmapShader shader;
    private WeakReference<Bitmap> bitmapRef = new WeakReference<>(null);
    /** Свой снимок (шторка); если задан — используется вместо общего. */
    private Bitmap ownBitmap;
    private boolean ownBitmapSet;
    private int tintColor;
    private int fallbackColor;
    private int strokeColor;
    private float strokeWidth;
    private float cornerRadius;
    private int alpha255 = 255;

    public SnapshotFrostDrawable(@Nullable View host) {
        hostRef = new WeakReference<>(host);
    }

    public SnapshotFrostDrawable setBitmap(@Nullable Bitmap bitmap) {
        ownBitmapSet = true;
        ownBitmap = bitmap != null && !bitmap.isRecycled() ? bitmap : null;
        invalidateSelf();
        return this;
    }

    public SnapshotFrostDrawable setHost(@Nullable View host) {
        hostRef = new WeakReference<>(host);
        invalidateSelf();
        return this;
    }

    /** Копия для другого вида-хозяина (окно реакций) с теми же цветами. */
    public SnapshotFrostDrawable copyForHost(@Nullable View host) {
        SnapshotFrostDrawable copy = new SnapshotFrostDrawable(host);
        copy.tintColor = tintColor;
        copy.fallbackColor = fallbackColor;
        copy.strokeColor = strokeColor;
        copy.strokeWidth = strokeWidth;
        copy.cornerRadius = cornerRadius;
        copy.ownBitmapSet = ownBitmapSet;
        copy.ownBitmap = ownBitmap;
        return copy;
    }

    public SnapshotFrostDrawable setCornerRadius(float radius) {
        cornerRadius = Math.max(0, radius);
        invalidateSelf();
        return this;
    }

    /** Полупрозрачная тонировка поверх снимка (обычно цвет бабла). */
    public SnapshotFrostDrawable setTintColor(int color) {
        tintColor = color;
        invalidateSelf();
        return this;
    }

    /** Непрозрачная заливка, когда снимка нет (сплошные обои, capturing failed). */
    public SnapshotFrostDrawable setFallbackColor(int color) {
        fallbackColor = color;
        invalidateSelf();
        return this;
    }

    /** Волосатая кромка по верхнему краю — даёт панели читаемый объём. */
    public SnapshotFrostDrawable setStroke(int color, float width) {
        strokeColor = color;
        strokeWidth = Math.max(0, width);
        invalidateSelf();
        return this;
    }

    @Override
    public void setAlpha(int alpha) {
        alpha255 = alpha;
        invalidateSelf();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        boundsF.set(bounds);
        path.rewind();
        path.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW);

        float alpha = alpha255 / 255f;
        Bitmap bitmap = ownBitmapSet ? ownBitmap : getSnapshot();
        if (bitmap != null) {
            if (bitmap != bitmapRef.get()) {
                bitmapRef = new WeakReference<>(bitmap);
                shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                frostPaint.setShader(shader);
            }
            // Собственный снимок привязан к окну; общий может рисоваться в другом popup-окне.
            View host = hostRef.get();
            canvas.getMatrix(canvasMatrix);
            if (!canvasMatrix.invert(shaderMatrix)) {
                shaderMatrix.reset();
            }
            float tx = 0f, ty = 0f;
            if (host != null && host.isAttachedToWindow()) {
                originPoint[0] = originPoint[1] = 0f;
                canvasMatrix.mapPoints(originPoint);
                if (ownBitmapSet) {
                    host.getLocationInWindow(screenLocation);
                } else {
                    host.getLocationOnScreen(screenLocation);
                }
                tx = originPoint[0] - screenLocation[0];
                ty = originPoint[1] - screenLocation[1];
            }
            int screenW = host != null && host.getRootView() != null && host.getRootView().getWidth() > 0
                    ? host.getRootView().getWidth() : AndroidUtilities.displaySize.x;
            int screenH = host != null && host.getRootView() != null && host.getRootView().getHeight() > 0
                    ? host.getRootView().getHeight() : AndroidUtilities.displaySize.y;
            float sux = screenW / (float) bitmap.getWidth();
            float suy = screenH / (float) bitmap.getHeight();
            tmpMatrix.reset();
            tmpMatrix.setScale(sux, suy);
            tmpMatrix.postTranslate(tx, ty);
            shaderMatrix.preConcat(tmpMatrix);
            shader.setLocalMatrix(shaderMatrix);
            frostPaint.setAlpha((int) (255 * alpha));
            canvas.drawPath(path, frostPaint);
            if (Color.alpha(tintColor) > 0) {
                tintPaint.setColor(ColorUtils.setAlphaComponent(tintColor, (int) (Color.alpha(tintColor) * alpha)));
                canvas.drawPath(path, tintPaint);
            }
        } else {
            int color = fallbackColor != 0 ? fallbackColor : tintColor;
            if (color != 0) {
                tintPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha)));
                canvas.drawPath(path, tintPaint);
            }
        }

        if (strokeWidth > 0 && Color.alpha(strokeColor) > 0) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setColor(ColorUtils.setAlphaComponent(strokeColor, (int) (Color.alpha(strokeColor) * alpha)));
            boundsF.inset(strokeWidth / 2f, strokeWidth / 2f);
            path.rewind();
            path.addRoundRect(boundsF, Math.max(0, cornerRadius - strokeWidth / 2f), Math.max(0, cornerRadius - strokeWidth / 2f), Path.Direction.CW);
            canvas.drawPath(path, strokePaint);
        }
    }
}
