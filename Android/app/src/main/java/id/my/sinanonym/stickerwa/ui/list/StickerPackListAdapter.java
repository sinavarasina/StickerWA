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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @Nullable
    private OnSelectionChangedListener onSelectionChangedListener;

    private final Set<String> selectedPackIdentifiers = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnAddButtonClickedListener {
        void onAddButtonClicked(StickerPack stickerPack);
    }

    public interface OnPackCardClickedListener {
        void onPackCardClicked(StickerPack stickerPack);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public StickerPackListAdapter(
            @NonNull List<StickerPack> stickerPacks,
            @NonNull OnAddButtonClickedListener onAddButtonClickedListener,
            @NonNull OnPackCardClickedListener onPackCardClickedListener) {
        this.stickerPacks = stickerPacks;
        this.onAddButtonClickedListener = onAddButtonClickedListener;
        this.onPackCardClickedListener = onPackCardClickedListener;
    }

    public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener listener) {
        this.onSelectionChangedListener = listener;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public int getSelectedCount() {
        return selectedPackIdentifiers.size();
    }

    @NonNull
    public List<StickerPack> getSelectedPacks() {
        List<StickerPack> list = new ArrayList<>();
        for (StickerPack pack : stickerPacks) {
            if (selectedPackIdentifiers.contains(pack.identifier)) {
                list.add(pack);
            }
        }
        return list;
    }

    public void enterSelectionMode(@NonNull StickerPack initialPack) {
        if (!initialPack.isCustom()) {
            return;
        }
        isSelectionMode = true;
        selectedPackIdentifiers.clear();
        selectedPackIdentifiers.add(initialPack.identifier);
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedPackIdentifiers.size());
        }
    }

    public void toggleSelection(@NonNull StickerPack pack) {
        if (!pack.isCustom()) {
            return;
        }
        if (selectedPackIdentifiers.contains(pack.identifier)) {
            selectedPackIdentifiers.remove(pack.identifier);
            if (selectedPackIdentifiers.isEmpty()) {
                exitSelectionMode();
                return;
            }
        } else {
            selectedPackIdentifiers.add(pack.identifier);
        }
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedPackIdentifiers.size());
        }
    }

    public void exitSelectionMode() {
        if (!isSelectionMode) {
            return;
        }
        isSelectionMode = false;
        selectedPackIdentifiers.clear();
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(0);
        }
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
        if (viewHolder.countView != null) {
            viewHolder.countView.setText(context.getResources().getQuantityString(R.plurals.card_sticker_count_plural, count, count));
        }
        viewHolder.filesizeView.setText(sizeText);

        viewHolder.trayIconView.setImageURI(
                StickerRepository.getStickerAssetUri(pack.identifier, pack.trayImageFile)
        );

        viewHolder.animatedStickerPackIndicator.setVisibility(pack.animatedStickerPack ? View.VISIBLE : View.GONE);
        if (viewHolder.customBadgeView != null) {
            viewHolder.customBadgeView.setVisibility(pack.isCustom() ? View.VISIBLE : View.GONE);
        }

        // Selection mode visual appearance
        if (isSelectionMode) {
            viewHolder.addButton.setVisibility(View.GONE);
            viewHolder.selectionCheckIndicator.setVisibility(View.VISIBLE);

            boolean isSelected = selectedPackIdentifiers.contains(pack.identifier);
            float density = context.getResources().getDisplayMetrics().density;

            if (isSelected) {
                int colorPrimary = getColorPrimary(context);
                viewHolder.cardView.setStrokeWidth((int) (2.5f * density));
                viewHolder.cardView.setStrokeColor(colorPrimary);
                viewHolder.selectionCheckIndicator.setImageResource(R.drawable.ic_check_circle);
                viewHolder.selectionCheckIndicator.setAlpha(1.0f);
            } else {
                viewHolder.cardView.setStrokeWidth(0);
                viewHolder.selectionCheckIndicator.setImageResource(R.drawable.ic_check_circle);
                viewHolder.selectionCheckIndicator.setAlpha(0.25f);
            }
        } else {
            viewHolder.cardView.setStrokeWidth(0);
            viewHolder.selectionCheckIndicator.setVisibility(View.GONE);
            viewHolder.addButton.setVisibility(View.VISIBLE);
            setAddButtonAppearance(viewHolder.addButton, pack);
        }

        viewHolder.container.setOnClickListener(v -> {
            if (isSelectionMode) {
                if (pack.isCustom()) {
                    toggleSelection(pack);
                } else {
                    Toast.makeText(context, R.string.select_custom_only_notice, Toast.LENGTH_SHORT).show();
                }
            } else {
                onPackCardClickedListener.onPackCardClicked(pack);
            }
        });

        viewHolder.container.setOnLongClickListener(v -> {
            if (pack.isCustom()) {
                if (!isSelectionMode) {
                    enterSelectionMode(pack);
                } else {
                    toggleSelection(pack);
                }
                return true;
            } else {
                Toast.makeText(context, R.string.select_custom_only_notice, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    private static int getColorPrimary(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
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
        public final MaterialCardView cardView;
        public final TextView titleView;
        public final TextView publisherView;
        public final TextView countView;
        public final TextView filesizeView;
        public final ImageView addButton;
        public final ImageView selectionCheckIndicator;
        public final ImageView animatedStickerPackIndicator;
        public final TextView customBadgeView;
        public final SimpleDraweeView trayIconView;

        ViewHolder(final View itemView) {
            super(itemView);
            container = itemView;
            cardView = (MaterialCardView) itemView;
            titleView = itemView.findViewById(R.id.sticker_pack_title);
            publisherView = itemView.findViewById(R.id.sticker_pack_publisher);
            countView = itemView.findViewById(R.id.sticker_pack_count);
            filesizeView = itemView.findViewById(R.id.sticker_pack_filesize);
            addButton = itemView.findViewById(R.id.add_button_on_list);
            selectionCheckIndicator = itemView.findViewById(R.id.selection_check_indicator);
            trayIconView = itemView.findViewById(R.id.sticker_pack_tray_icon);
            animatedStickerPackIndicator = itemView.findViewById(R.id.sticker_pack_animation_indicator);
            customBadgeView = itemView.findViewById(R.id.sticker_pack_custom_badge);
        }
    }
}
