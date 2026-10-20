package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AudioCodecs;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
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
        } else if (position == audioCodecRow) {
            showAudioCodecDialog();
            return;
        } else if (position == voiceBitrateRow) {
            showVoiceBitrateDialog();
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
            if (position == headerRow || position == typingHeaderRow
                    || position == privacyHeaderRow || position == mediaHeaderRow) {
                return TYPE_HEADER;
            } else if (position == infoRow || position == typingInfoRow
                    || position == privacyInfoRow || position == mediaInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        cell.setText(getString(R.string.OEDiveHeader));
                    } else if (position == typingHeaderRow) {
                        cell.setText(getString(R.string.OEDiveTypingHeader));
                    } else if (position == privacyHeaderRow) {
                        cell.setText(getString(R.string.OEDivePrivacyHeader));
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
                    } else if (position == autoReplyRow) {
                        cell.setTextAndValue(getString(R.string.OEDiveAutoReplyTitle),
                                SharedConfig.autoReplyEnabled
                                        ? getString(R.string.OEDiveOn) : getString(R.string.OEDiveOff), true, true);
                    }
                    break;
                }
            }
        }
    }
}
