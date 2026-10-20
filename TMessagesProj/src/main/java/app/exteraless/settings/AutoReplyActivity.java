package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Автоответчик (DiveGram): автоматические ответы в личных чатах.
 * Перенесён из DiveGram, адаптирован под экраны форка через BaseNekoSettingsActivity.
 */
public class AutoReplyActivity extends BaseNekoSettingsActivity {

    private int enableRow;
    private int textRow;
    private int cooldownRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        enableRow = addRow("autoReplyEnable");
        textRow = addRow("autoReplyText");
        cooldownRow = addRow("autoReplyCooldown");
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEAutoReplyTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == enableRow) {
            SharedConfig.setAutoReplyEnabled(!SharedConfig.autoReplyEnabled);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(SharedConfig.autoReplyEnabled);
            }
        } else if (position == textRow) {
            showTextInputDialog();
        } else if (position == cooldownRow) {
            showCooldownDialog();
        }
    }

    private void showTextInputDialog() {
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEAutoReplyTextTitle));

        final android.widget.EditText editText = new android.widget.EditText(getParentActivity());
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        editText.setHint(getString(R.string.OEAutoReplyTextHint));
        editText.setMaxLines(8);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setText(SharedConfig.autoReplyText);
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                SharedConfig.setAutoReplyText(s.toString());
            }
        });

        android.widget.FrameLayout container = new android.widget.FrameLayout(getParentActivity());
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.OK), (d, w) -> {
            SharedConfig.setAutoReplyText(editText.getText().toString());
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCooldownDialog() {
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEAutoReplyCooldownTitle));
        String[] options = {"1 минута", "5 минут", "10 минут", "30 минут", "1 час"};
        int[] values = {60, 300, 600, 1800, 3600};
        builder.setItems(options, (d, which) -> {
            SharedConfig.setAutoReplyCooldownSec(values[which]);
            listView.getAdapter().notifyItemChanged(cooldownRow);
        });
        showDialog(builder.create());
    }

    private static String cooldownName(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + " ч.";
        }
        if (seconds >= 60) {
            return (seconds / 60) + " мин.";
        }
        return seconds + " сек.";
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return infoRow + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableRow || position == textRow || position == cooldownRow) {
                return TYPE_CHECK;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != TYPE_INFO_PRIVACY;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_CHECK) {
                TextCheckCell cell = new TextCheckCell(mContext, getResourceProvider());
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                view = cell;
            } else {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(mContext, getResourceProvider());
                view = cell;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == enableRow) {
                    cell.setTextAndCheckAndSubText(
                            getString(R.string.OEAutoReplyEnable),
                            getString(R.string.OEAutoReplyInfo),
                            SharedConfig.autoReplyEnabled, true);
                } else if (position == textRow) {
                    cell.setTextAndValue(
                            getString(R.string.OEAutoReplyTextTitle),
                            SharedConfig.autoReplyText.isEmpty()
                                    ? getString(R.string.OEAutoReplyTextHint)
                                    : SharedConfig.autoReplyText, true, true);
                } else if (position == cooldownRow) {
                    cell.setTextAndValue(
                            getString(R.string.OEAutoReplyCooldownTitle),
                            cooldownName(SharedConfig.autoReplyCooldownSec), true, true);
                }
            } else if (type == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(getString(R.string.OEAutoReplyInfo));
                cell.setBackground(Theme.getThemedDrawableByKey(mContext,
                        R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }
    }
}
