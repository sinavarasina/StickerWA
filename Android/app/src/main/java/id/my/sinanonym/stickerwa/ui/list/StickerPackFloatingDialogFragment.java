package id.my.sinanonym.stickerwa.ui.list;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;
import java.util.concurrent.Future;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.core.StickerStorageHelper;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.Sticker;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.details.StickerPackDetailsActivity;
import id.my.sinanonym.stickerwa.ui.info.StickerPackInfoActivity;
import id.my.sinanonym.stickerwa.whatsapp.WhatsAppStickerLauncher;
import id.my.sinanonym.stickerwa.whatsapp.WhitelistCheck;

/**
 * Large flat floating card displaying a big sticker preview, horizontal sticker selector strip,
 * save-to-storage button, and add-to-WhatsApp (+) button.
 */
public class StickerPackFloatingDialogFragment extends DialogFragment {

    private static final String ARG_STICKER_PACK = "arg_sticker_pack";
    private static final String STATE_SELECTED_INDEX = "state_selected_index";

    private StickerPack stickerPack;
    private int selectedStickerIndex = 0;

    private SimpleDraweeView largeStickerPreview;
    private View addButton;
    private View alreadyAddedBadge;
    private HorizontalStickerAdapter horizontalAdapter;
    @Nullable
    private Future<?> whitelistTaskFuture;

    public static StickerPackFloatingDialogFragment newInstance(@NonNull StickerPack pack) {
        StickerPackFloatingDialogFragment fragment = new StickerPackFloatingDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_STICKER_PACK, pack);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stickerPack = getArguments().getParcelable(ARG_STICKER_PACK);
        }
        if (savedInstanceState != null) {
            selectedStickerIndex = savedInstanceState.getInt(STATE_SELECTED_INDEX, 0);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_INDEX, selectedStickerIndex);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            // Flat UI: remove semi-transparent window dimming
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = window.getAttributes();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                } else {
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                window.setAttributes(lp);
            }
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_floating_sticker_pack, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (stickerPack == null) {
            dismissAllowingStateLoss();
            return;
        }

        SimpleDraweeView trayIcon = view.findViewById(R.id.floating_tray_icon);
        TextView packName = view.findViewById(R.id.floating_pack_name);
        TextView packSubtitle = view.findViewById(R.id.floating_pack_subtitle);
        View infoButton = view.findViewById(R.id.floating_info_button);
        View closeButton = view.findViewById(R.id.floating_close_button);
        largeStickerPreview = view.findViewById(R.id.floating_large_sticker_preview);
        View saveStickerButton = view.findViewById(R.id.floating_save_sticker_button);
        addButton = view.findViewById(R.id.floating_add_to_whatsapp_button);
        alreadyAddedBadge = view.findViewById(R.id.floating_already_added_badge);
        RecyclerView horizontalRecycler = view.findViewById(R.id.floating_horizontal_sticker_list);

        Uri trayUri = StickerRepository.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile);
        trayIcon.setImageURI(trayUri);
        packName.setText(stickerPack.name);

        String formattedSize = Formatter.formatShortFileSize(requireContext(), stickerPack.getTotalSize());
        int count = stickerPack.getStickers() != null ? stickerPack.getStickers().size() : 0;
        packSubtitle.setText(getString(R.string.floating_card_subtitle_format, stickerPack.publisher, count, formattedSize));

        closeButton.setOnClickListener(v -> dismiss());
        infoButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), StickerPackInfoActivity.class);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_ID, stickerPack.identifier);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_WEBSITE, stickerPack.publisherWebsite);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_EMAIL, stickerPack.publisherEmail);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_PRIVACY_POLICY, stickerPack.privacyPolicyWebsite);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_LICENSE_AGREEMENT, stickerPack.licenseAgreementWebsite);
            intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_TRAY_ICON, trayUri.toString());
            startActivity(intent);
        });

        // Setup Horizontal Sticker List inside bottom of card
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        horizontalRecycler.setLayoutManager(layoutManager);
        horizontalAdapter = new HorizontalStickerAdapter(
                stickerPack.identifier,
                stickerPack.getStickers(),
                selectedStickerIndex,
                position -> {
                    selectedStickerIndex = position;
                    showSelectedStickerInLargePreview();
                }
        );
        horizontalRecycler.setAdapter(horizontalAdapter);

        // Display initial selected sticker in the huge viewer
        showSelectedStickerInLargePreview();

        // Save currently previewed sticker to device storage
        saveStickerButton.setOnClickListener(v -> {
            List<Sticker> stickers = stickerPack.getStickers();
            if (stickers != null && selectedStickerIndex >= 0 && selectedStickerIndex < stickers.size()) {
                Sticker current = stickers.get(selectedStickerIndex);
                StickerStorageHelper.saveStickerToGallery(
                        requireContext(),
                        stickerPack.identifier,
                        current.imageFileName,
                        stickerPack.animatedStickerPack
                );
            }
        });

        // Add pack to WhatsApp (+)
        addButton.setOnClickListener(v -> {
            if (getActivity() instanceof AppCompatActivity) {
                WhatsAppStickerLauncher.addStickerPackToWhatsApp(
                        (AppCompatActivity) getActivity(),
                        stickerPack.identifier,
                        stickerPack.name
                );
            }
        });

        updateWhitelistUI(stickerPack.getIsWhitelisted());
    }

    private void showSelectedStickerInLargePreview() {
        List<Sticker> stickers = stickerPack.getStickers();
        if (stickers == null || stickers.isEmpty()) {
            return;
        }
        if (selectedStickerIndex < 0 || selectedStickerIndex >= stickers.size()) {
            selectedStickerIndex = 0;
        }
        Sticker selected = stickers.get(selectedStickerIndex);
        Uri stickerUri = StickerRepository.getStickerAssetUri(stickerPack.identifier, selected.imageFileName);
        DraweeController controller = Fresco.newDraweeControllerBuilder()
                .setUri(stickerUri)
                .setAutoPlayAnimations(true)
                .build();
        largeStickerPreview.setController(controller);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (stickerPack != null) {
            final Context appContext = requireContext().getApplicationContext();
            final String packId = stickerPack.identifier;
            whitelistTaskFuture = AppExecutors.execute(
                    () -> WhitelistCheck.isWhitelisted(appContext, packId),
                    this::updateWhitelistUI
            );
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AppExecutors.cancel(whitelistTaskFuture);
    }

    private void updateWhitelistUI(Boolean isWhitelisted) {
        boolean added = Boolean.TRUE.equals(isWhitelisted);
        if (stickerPack != null) {
            stickerPack.setIsWhitelisted(added);
        }
        if (addButton != null && alreadyAddedBadge != null) {
            addButton.setVisibility(added ? View.GONE : View.VISIBLE);
            alreadyAddedBadge.setVisibility(added ? View.VISIBLE : View.GONE);
        }
    }

    private static class HorizontalStickerAdapter extends RecyclerView.Adapter<HorizontalStickerAdapter.ThumbViewHolder> {

        interface OnStickerSelectedListener {
            void onStickerSelected(int position);
        }

        private final String packIdentifier;
        private final List<Sticker> stickers;
        private int selectedPosition;
        private final OnStickerSelectedListener listener;

        HorizontalStickerAdapter(
                @NonNull String packIdentifier,
                @NonNull List<Sticker> stickers,
                int initialSelected,
                @NonNull OnStickerSelectedListener listener) {
            this.packIdentifier = packIdentifier;
            this.stickers = stickers;
            this.selectedPosition = initialSelected;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ThumbViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_floating_sticker_thumb, parent, false);
            return new ThumbViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ThumbViewHolder holder, int position) {
            Sticker sticker = stickers.get(position);
            Uri uri = StickerRepository.getStickerAssetUri(packIdentifier, sticker.imageFileName);
            holder.imageView.setImageURI(uri);
            boolean isSelected = (position == selectedPosition);
            holder.container.setBackgroundResource(
                    isSelected ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected
            );
            holder.container.setOnClickListener(v -> {
                int oldPos = selectedPosition;
                int newPos = holder.getBindingAdapterPosition();
                if (newPos == RecyclerView.NO_POSITION) {
                    return;
                }
                selectedPosition = newPos;
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
                listener.onStickerSelected(selectedPosition);
            });
        }

        @Override
        public int getItemCount() {
            return stickers != null ? stickers.size() : 0;
        }

        static class ThumbViewHolder extends RecyclerView.ViewHolder {
            final View container;
            final SimpleDraweeView imageView;

            ThumbViewHolder(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.thumb_container);
                imageView = itemView.findViewById(R.id.thumb_sticker_image);
            }
        }
    }
}
