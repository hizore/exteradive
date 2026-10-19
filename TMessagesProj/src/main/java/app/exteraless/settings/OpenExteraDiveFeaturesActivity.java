package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ActionBar.Theme;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Значки, серии и NFT — функции, перенесённые из DiveLite.
 *
 * Экран написан свой вместо переноса экрана DiveLite: тот тянул за собой сорок
 * флагов от ghost-режима до обоев, а нужны три переключателя. Экрана в самом
 * exteraless не было — там функции открывались из меню MineGram, которого у нас
 * тоже нет, поэтому точка входа своя.
 */
public class OpenExteraDiveFeaturesActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int badgesRow;
    private int streaksRow;
    private int nftsRow;
    private int typingHeaderRow;
    private int showTypingLocationRow;
    private int typingInfoRow;
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
        boolean enabled;
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
        } else {
            return;
        }

        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
        }

        // Значки рисуются прямо в ChatMessageCell, поэтому после переключения
        // нужно перерисовать открытые чаты, а не только этот экран.
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == typingHeaderRow) {
                return TYPE_HEADER;
            } else if (position == infoRow || position == typingInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(getString(position == typingHeaderRow
                            ? R.string.OEDiveTypingHeader : R.string.OEDiveHeader));
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == typingInfoRow) {
                        cell.setText(getString(R.string.OEDiveTypingInfo));
                    } else {
                        cell.setText(getString(R.string.OEDiveInfo));
                    }
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
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
                    }
                    break;
                }
            }
        }
    }
}
