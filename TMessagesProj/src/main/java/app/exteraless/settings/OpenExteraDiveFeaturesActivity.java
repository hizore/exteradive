package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AudioCodecs;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Функции DiveGram/DiveLite: значки, серии, NFT, настройки списка и чата.
 *
 * Экран написан свой вместо переноса экрана DiveGram: у него четыре вкладки
 * на сорок флагов от ghost-режима до обоев, а нужны отдельные переключатели.
 * Визуал настроек оставлен как в форке.
 */
public class OpenExteraDiveFeaturesActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int badgesRow;
    private int streaksRow;
    private int nftsRow;
    private int typingHeaderRow;
    private int showTypingLocationRow;
    private int typingInfoRow;
    private int privacyHeaderRow;
    private int mentionAllRow;
    private int mentionAllSplitRow;
    private int privacyInfoRow;
    private int mediaHeaderRow;
    private int audioCodecRow;
    private int voiceBitrateRow;
    private int playerBitrateRow;
    private int mediaInfoRow;
    private int animationsHeaderRow;
    private int disableAnimationsRow;
    private int animationsInfoRow;
    private int deletedHeaderRow;
    private int saveDeletedRow;
    private int deletedOpacityRow;
    private int purgeDeletedRow;
    private int deletedInfoRow;
    private int autoReplyRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = addRow("diveHeader");
        badgesRow = addRow("diveBadges");
        streaksRow = addRow("diveStreaks");
        nftsRow = addRow("diveNfts");

        typingHeaderRow = addRow("diveTypingHeader");
        showTypingLocationRow = addRow("diveShowTypingLocation");
        typingInfoRow = addRow();

        privacyHeaderRow = addRow("divePrivacyHeader");
        mentionAllRow = addRow("diveMentionAll");
        mentionAllSplitRow = addRow("diveMentionAllSplit");
        privacyInfoRow = addRow();

        mediaHeaderRow = addRow("diveMediaHeader");
        audioCodecRow = addRow("diveAudioCodec");
        voiceBitrateRow = addRow("diveVoiceBitrate");
        playerBitrateRow = addRow("divePlayerBitrate");
        mediaInfoRow = addRow();

        animationsHeaderRow = addRow("diveAnimationsHeader");
        disableAnimationsRow = addRow("diveDisableAnimations");
        animationsInfoRow = addRow();

        // exteradive: ветка "Удалённые сообщения" скрыта - сами строки из списка
        // убраны, индексы остаются -1, чтобы ни одна проверка не сработала.
        deletedHeaderRow = -1;
        saveDeletedRow = -1;
        deletedOpacityRow = -1;
        purgeDeletedRow = -1;
        deletedInfoRow = -1;

        autoReplyRow = addRow("diveAutoReply");
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEDiveHeader);
    }

    @Override
    protected String getKey() {
        return "exteraless_dive_features";
    }

    @Override
    public int getSearchGuid() {
        return 25100;
    }

    @Override
    public String getSearchPrefix() {
        return "OEDive";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        handleRowClick(position, view);
    }

    /** Защита от двойного срабатывания: клик по ячейке и по списку могут прийти вместе. */
    private long lastClickTime;
    private int lastClickPosition = -1;

    private void handleRowClick(int position, View view) {
        long now = System.currentTimeMillis();
        if (position == lastClickPosition && now - lastClickTime < 600) {
            return;
        }
        lastClickPosition = position;
        lastClickTime = now;

        boolean enabled = false;
        if (position == badgesRow) {
            SharedConfig.badgesEnabled = !SharedConfig.badgesEnabled;
            SharedConfig.saveBadgesFetchTime();
            enabled = SharedConfig.badgesEnabled;
        } else if (position == streaksRow) {
            SharedConfig.streaksEnabled = !SharedConfig.streaksEnabled;
            SharedConfig.saveStreakFetchTime();
            enabled = SharedConfig.streaksEnabled;
        } else if (position == nftsRow) {
            SharedConfig.nftsEnabled = !SharedConfig.nftsEnabled;
            SharedConfig.saveNftsFetchTime();
            enabled = SharedConfig.nftsEnabled;
        } else if (position == showTypingLocationRow) {
            SharedConfig.setShowTypingLocation(!SharedConfig.showTypingLocation);
            enabled = SharedConfig.showTypingLocation;
        } else if (position == mentionAllRow) {
            SharedConfig.setMentionAllEnabled(!SharedConfig.mentionAllEnabled);
            enabled = SharedConfig.mentionAllEnabled;
        } else if (position == mentionAllSplitRow) {
            SharedConfig.setMentionAllSplitEnabled(!SharedConfig.mentionAllSplitEnabled);
            enabled = SharedConfig.mentionAllSplitEnabled;
        } else if (position == playerBitrateRow) {
            SharedConfig.setPlayerBitrate(!SharedConfig.playerBitrate);
            enabled = SharedConfig.playerBitrate;
        } else if (position == disableAnimationsRow) {
            // exteradive: переключатель "Отключить анимации". В SharedConfig флаг означает
            // обратное - "анимации разрешены", поэтому инвертируем его.
            SharedConfig.setAnimationsEnabled(!SharedConfig.animationsEnabled());
            enabled = !SharedConfig.animationsEnabled();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            // сообщаем плееру и остальным экранам, что режим анимаций сменился
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.animationsToggled);
            NotificationCenter.getInstance(UserConfig.selectedAccount)
                    .postNotificationName(NotificationCenter.animationsToggled);
            return;
        } else if (position == audioCodecRow) {
            showAudioCodecDialog();
            return;
        } else if (position == voiceBitrateRow) {
            showVoiceBitrateDialog();
            return;
        } else if (position == saveDeletedRow) {
            SharedConfig.setSaveDeletedMessages(!SharedConfig.saveDeletedMessages);
            enabled = SharedConfig.saveDeletedMessages;
            if (!enabled) {
                // выключили - сохранённые удалённые сообщения убираем из чатов
                MessagesController.getInstance(UserConfig.selectedAccount).purgeSavedDeletedMessages();
                Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                        getString(R.string.OEDiveSavedDeletedCleared), Toast.LENGTH_SHORT).show();
            }
            listView.getAdapter().notifyItemChanged(deletedOpacityRow);
            listView.getAdapter().notifyItemChanged(purgeDeletedRow);
        } else if (position == purgeDeletedRow) {
            showPurgeDeletedDialog();
            return;
        } else if (position == autoReplyRow) {
            presentFragment(new AutoReplyActivity());
            return;
        } else {
            return;
        }

        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
        }

        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }

    private void showAudioCodecDialog() {
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEDiveAudioCodecTitle));
        builder.setItems(AudioCodecs.CODEC_TITLES, (dialog, which) -> {
            if (which >= 0 && which < AudioCodecs.getCodecCount()) {
                SharedConfig.setAudioCodec(which);
                listView.getAdapter().notifyItemChanged(audioCodecRow);
            }
        });
        showDialog(builder.create());
    }

    private void showVoiceBitrateDialog() {
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEDiveVoiceBitrateTitle));
        builder.setItems(AudioCodecs.VOICE_BITRATE_TITLES, (dialog, which) -> {
            if (which >= 0 && which < AudioCodecs.VOICE_BITRATE_VALUES.length) {
                SharedConfig.setVoiceRecordBitrate(AudioCodecs.VOICE_BITRATE_VALUES[which]);
                listView.getAdapter().notifyItemChanged(voiceBitrateRow);
            }
        });
        showDialog(builder.create());
    }

    /** exteradive: удаление всех сохранённых удалённых сообщений. */
    private void showPurgeDeletedDialog() {
        final int count = SharedConfig.getDeletedMessageKeysCopy().size();
        if (getParentActivity() == null) {
            return;
        }
        if (count == 0) {
            Toast.makeText(getParentActivity(), getString(R.string.OEDiveNoSavedDeleted),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEDivePurgeDeletedTitle));
        builder.setMessage(String.format(getString(R.string.OEDivePurgeDeletedText), count));
        builder.setPositiveButton(getString(R.string.OEDiveDelete), (dialog, which) -> {
            MessagesController.getInstance(UserConfig.selectedAccount).purgeSavedDeletedMessages();
            NotificationCenter.getInstance(UserConfig.selectedAccount)
                    .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            listView.getAdapter().notifyItemChanged(deletedOpacityRow);
            listView.getAdapter().notifyItemChanged(purgeDeletedRow);
            Toast.makeText(getParentActivity(), getString(R.string.OEDiveSavedDeletedCleared),
                    Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class ListAdapter extends BaseListAdapter {

        /** exteradive: тип ячейки со слайдером (прозрачность удалённых сообщений). */
        private static final int TYPE_SLIDER = 100;

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return infoRow + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == typingHeaderRow
                    || position == privacyHeaderRow || position == mediaHeaderRow
                    || position == animationsHeaderRow || position == deletedHeaderRow) {
                return TYPE_HEADER;
            } else if (position == infoRow || position == typingInfoRow
                    || position == privacyInfoRow || position == mediaInfoRow
                    || position == animationsInfoRow || position == deletedInfoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == deletedOpacityRow) {
                return TYPE_SLIDER;
            }
            return TYPE_CHECK;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_SLIDER) {
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(4),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(4));
                android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                        AndroidUtilities.dp(16), AndroidUtilities.dp(10));
                container.setLayoutParams(lp);
                container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                android.widget.TextView title = new android.widget.TextView(mContext);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
                container.addView(title, new android.widget.LinearLayout.LayoutParams(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT));

                org.telegram.ui.Components.SeekBarView seekBar =
                        new org.telegram.ui.Components.SeekBarView(mContext);
                seekBar.setReportChanges(true);
                android.widget.LinearLayout.LayoutParams seekLp = new android.widget.LinearLayout.LayoutParams(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT);
                seekLp.topMargin = AndroidUtilities.dp(8);
                container.addView(seekBar, seekLp);

                android.widget.TextView value = new android.widget.TextView(mContext);
                value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
                value.setGravity(android.view.Gravity.RIGHT);
                container.addView(value, new android.widget.LinearLayout.LayoutParams(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT));

                container.setTag(new Object[]{seekBar, value, title});
                return new RecyclerListView.Holder(container);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() == TYPE_SLIDER) {
                bindOpacitySlider(holder.itemView);
                return;
            }
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        cell.setText(getString(R.string.OEDiveHeader));
                    } else if (position == typingHeaderRow) {
                        cell.setText(getString(R.string.OEDiveTypingHeader));
                    } else if (position == privacyHeaderRow) {
                        cell.setText(getString(R.string.OEDivePrivacyHeader));
                    } else if (position == animationsHeaderRow) {
                        cell.setText(getString(R.string.OEDiveAnimationsHeader));
                    } else if (position == deletedHeaderRow) {
                        cell.setText(getString(R.string.OEDiveDeletedHeader));
                    } else {
                        cell.setText(getString(R.string.OEDiveMediaHeader));
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == typingInfoRow) {
                        cell.setText(getString(R.string.OEDiveTypingInfo));
                    } else if (position == privacyInfoRow) {
                        cell.setText(getString(R.string.OEDivePrivacyInfo));
                    } else if (position == animationsInfoRow) {
                        cell.setText(getString(R.string.OEDiveAnimationsInfo));
                    } else if (position == deletedInfoRow) {
                        cell.setText(getString(R.string.OEDiveDeletedInfo));
                    } else if (position == mediaInfoRow) {
                        cell.setText(getString(R.string.OEDiveMediaInfo));
                    } else {
                        cell.setText(getString(R.string.OEDiveInfo));
                    }
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    // exteradive: клик вешаем прямо на ячейку. Список отдаёт клик
                    // не всем подряд, а на переключателе он не доходил.
                    cell.setOnClickListener(v -> handleRowClick(position, cell));
                    if (position == badgesRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveBadges),
                                SharedConfig.badgesEnabled, true);
                    } else if (position == streaksRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveStreaks),
                                SharedConfig.streaksEnabled, true);
                    } else if (position == nftsRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveNfts),
                                SharedConfig.nftsEnabled, true);
                    } else if (position == showTypingLocationRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveShowTypingLocation),
                                SharedConfig.showTypingLocation, true);
                    } else if (position == mentionAllRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveMentionAll),
                                SharedConfig.mentionAllEnabled, true);
                    } else if (position == mentionAllSplitRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveMentionAllSplit),
                                SharedConfig.mentionAllSplitEnabled, true);
                    } else if (position == audioCodecRow) {
                        cell.setTextAndValue(getString(R.string.OEDiveAudioCodecTitle),
                                AudioCodecs.CODEC_TITLES[SharedConfig.audioCodec], true, true);
                    } else if (position == voiceBitrateRow) {
                        cell.setTextAndValue(getString(R.string.OEDiveVoiceBitrateTitle),
                                AudioCodecs.VOICE_BITRATE_TITLES[AudioCodecs.getVoiceBitrateIndex()], true, true);
                    } else if (position == playerBitrateRow) {
                        cell.setTextAndCheck(getString(R.string.OEDivePlayerBitrate),
                                SharedConfig.playerBitrate, true);
                    } else if (position == disableAnimationsRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveDisableAnimations),
                                !SharedConfig.animationsEnabled(), true);
                    } else if (position == saveDeletedRow) {
                        cell.setTextAndCheck(getString(R.string.OEDiveSaveDeleted),
                                SharedConfig.saveDeletedMessages, true);
                    } else if (position == purgeDeletedRow) {
                        int count = SharedConfig.getDeletedMessageKeysCopy().size();
                        cell.setTextAndValue(getString(R.string.OEDivePurgeDeletedTitle),
                                count == 0 ? getString(R.string.OEDiveNoSavedDeleted)
                                        : String.valueOf(count), true, true);
                    } else if (position == autoReplyRow) {
                        cell.setTextAndValue(getString(R.string.OEDiveAutoReplyTitle),
                                SharedConfig.autoReplyEnabled
                                        ? getString(R.string.OEDiveOn) : getString(R.string.OEDiveOff), true, true);
                    }
                    break;
                }
            }
        }

        /** exteradive: слайдер прозрачности сохранённых удалённых сообщений. */
        private void bindOpacitySlider(android.view.View view) {
            Object[] tag = (Object[]) view.getTag();
            if (tag == null) {
                return;
            }
            org.telegram.ui.Components.SeekBarView seekBar =
                    (org.telegram.ui.Components.SeekBarView) tag[0];
            android.widget.TextView value = (android.widget.TextView) tag[1];
            android.widget.TextView title = (android.widget.TextView) tag[2];
            title.setText(getString(R.string.OEDiveDeletedOpacity));
            value.setText(SharedConfig.deletedMessagesOpacity + "%");
            seekBar.setProgress(SharedConfig.deletedMessagesOpacity / 100f);
            seekBar.setDelegate(new org.telegram.ui.Components.SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    SharedConfig.setDeletedMessagesOpacity(Math.round(progress * 100));
                    value.setText(SharedConfig.deletedMessagesOpacity + "%");
                    if (stop) {
                        NotificationCenter.getInstance(UserConfig.selectedAccount)
                                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
        }
    }
}
