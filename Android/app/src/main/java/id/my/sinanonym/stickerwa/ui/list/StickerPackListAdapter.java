/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.ui.list;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.StickerPack;

public class StickerPackListAdapter extends RecyclerView.Adapter<StickerPackListAdapter.ViewHolder> {
    @NonNull
    private List<StickerPack> stickerPacks;
    @NonNull
    private final OnAddButtonClickedListener onAddButtonClickedListener;
    @NonNull
    private final OnPackCardClickedListener onPackCardClickedListener;

    public interface OnAddButtonClickedListener {
        void onAddButtonClicked(StickerPack stickerPack);
    }

    public interface OnPackCardClickedListener {
        void onPackCardClicked(StickerPack stickerPack);
    }

    public StickerPackListAdapter(
            @NonNull List<StickerPack> stickerPacks,
            @NonNull OnAddButtonClickedListener onAddButtonClickedListener,
            @NonNull OnPackCardClickedListener onPackCardClickedListener) {
        this.stickerPacks = stickerPacks;
        this.onAddButtonClickedListener = onAddButtonClickedListener;
        this.onPackCardClickedListener = onPackCardClickedListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup viewGroup, final int i) {
        final Context context = viewGroup.getContext();
        final View stickerPackRow = LayoutInflater.from(context)
                .inflate(R.layout.sticker_packs_list_item, viewGroup, false);
        return new ViewHolder(stickerPackRow);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, final int index) {
        StickerPack pack = stickerPacks.get(index);
        final Context context = viewHolder.publisherView.getContext();

        viewHolder.titleView.setText(pack.name);
        viewHolder.publisherView.setText(pack.publisher);

        String sizeText = Formatter.formatShortFileSize(context, pack.getTotalSize());
        int count = pack.getStickers() != null ? pack.getStickers().size() : 0;
        viewHolder.filesizeView.setText(context.getString(R.string.card_badge_count_size_format, count, sizeText));

        viewHolder.trayIconView.setImageURI(
                StickerRepository.getStickerAssetUri(pack.identifier, pack.trayImageFile)
        );

        viewHolder.container.setOnClickListener(v -> onPackCardClickedListener.onPackCardClicked(pack));
        setAddButtonAppearance(viewHolder.addButton, pack);
        viewHolder.animatedStickerPackIndicator.setVisibility(pack.animatedStickerPack ? View.VISIBLE : View.GONE);
    }

    private void setAddButtonAppearance(ImageView addButton, StickerPack pack) {
        if (pack.getIsWhitelisted()) {
            addButton.setImageResource(R.drawable.sticker_3rdparty_added);
            addButton.setClickable(false);
            addButton.setOnClickListener(null);
            addButton.setBackgroundResource(R.drawable.btn_circle_tonal);
        } else {
            addButton.setImageResource(R.drawable.sticker_3rdparty_add);
            addButton.setOnClickListener(v -> onAddButtonClickedListener.onAddButtonClicked(pack));
            addButton.setBackgroundResource(R.drawable.btn_circle_tonal);
        }
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size();
    }

    public void setStickerPackList(@NonNull List<StickerPack> stickerPackList) {
        this.stickerPacks = stickerPackList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View container;
        public final TextView titleView;
        public final TextView publisherView;
        public final TextView filesizeView;
        public final ImageView addButton;
        public final ImageView animatedStickerPackIndicator;
        public final SimpleDraweeView trayIconView;

        ViewHolder(final View itemView) {
            super(itemView);
            container = itemView;
            titleView = itemView.findViewById(R.id.sticker_pack_title);
            publisherView = itemView.findViewById(R.id.sticker_pack_publisher);
            filesizeView = itemView.findViewById(R.id.sticker_pack_filesize);
            addButton = itemView.findViewById(R.id.add_button_on_list);
            trayIconView = itemView.findViewById(R.id.sticker_pack_tray_icon);
            animatedStickerPackIndicator = itemView.findViewById(R.id.sticker_pack_animation_indicator);
        }
    }
}
