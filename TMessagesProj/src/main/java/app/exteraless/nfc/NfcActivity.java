package app.exteraless.nfc;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.exteraless.appearance.MainTabsUiHelper;
import tw.nekomimi.nekogram.helpers.MainTabsHelper;

/**
 * exteradive: вкладка NFC.
 *
 * На экране одна кнопка - переход в выбранное платёжное приложение. Сам переход
 * и выбор приложения включаются в настройках (Ече -> DiveLite -> NFC).
 */
public class NfcActivity extends BaseFragment {

    private TextView appNameView;
    private View button;

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.OENfcTitle));

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.baseline_account_balance_wallet_24);
        icon.setColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        container.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(72), AndroidUtilities.dp(72)));

        TextView title = new TextView(context);
        title.setText(getString(R.string.OENfcTitle));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = AndroidUtilities.dp(16);
        container.addView(title, titleLp);

        TextView description = new TextView(context);
        description.setText(getString(R.string.OENfcDescription));
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = AndroidUtilities.dp(8);
        container.addView(description, descLp);

        appNameView = new TextView(context);
        appNameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        appNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        appNameView.setGravity(Gravity.CENTER);
        appNameView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = AndroidUtilities.dp(12);
        container.addView(appNameView, nameLp);

        TextView buttonView = new TextView(context);
        buttonView.setText(getString(R.string.OENfcOpenPayment));
        buttonView.setTextColor(Theme.getColor(Theme.key_buttonNeutralText));
        buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        buttonView.setGravity(Gravity.CENTER);
        buttonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14), AndroidUtilities.dp(24), AndroidUtilities.dp(14));
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Theme.getColor(Theme.key_buttonNeutral));
        buttonBg.setCornerRadius(AndroidUtilities.dp(12));
        buttonView.setBackground(buttonBg);
        buttonView.setOnClickListener(v -> openPaymentApp());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLp.topMargin = AndroidUtilities.dp(24);
        container.addView(buttonView, buttonLp);
        button = buttonView;

        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        containerLp.bottomMargin = AndroidUtilities.dp(MainTabsUiHelper.getTabsViewHeightDp());
        contentView.addView(container, containerLp);

        updateAppName();
        fragmentView = contentView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentView != null) {
            updateAppName();
        }
    }

    private void updateAppName() {
        if (appNameView == null) {
            return;
        }
        final String label = NfcPayment.getSystemDefaultPaymentLabel(getParentActivity());
        if (label.isEmpty()) {
            appNameView.setText(getString(R.string.OENfcNoAppSelected));
        } else {
            appNameView.setText(String.format(getString(R.string.OENfcSelectedApp), label));
        }
    }

    private void openPaymentApp() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        if (!SharedConfig.nfcPaymentEnabled) {
            Toast.makeText(context, getString(R.string.OENfcDisabled), Toast.LENGTH_SHORT).show();
            return;
        }
        final String pkg = NfcPayment.getSystemDefaultPaymentApp(context);
        if (pkg.isEmpty()) {
            // в системе платёжное приложение не выбрано - открываем системный выбор
            NfcPayment.openSystemPaymentChooser(context);
            return;
        }
        if (!NfcPayment.launch(context, pkg)) {
            Toast.makeText(context, getString(R.string.OENfcLaunchFailed), Toast.LENGTH_SHORT).show();
        }
    }
}
