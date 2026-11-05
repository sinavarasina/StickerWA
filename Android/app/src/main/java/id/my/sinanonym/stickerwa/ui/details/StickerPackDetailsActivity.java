/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.ui.details;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.concurrent.Future;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.core.StickerPackExporter;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;
import id.my.sinanonym.stickerwa.ui.info.StickerPackInfoActivity;
import id.my.sinanonym.stickerwa.ui.settings.SettingsActivity;
import id.my.sinanonym.stickerwa.whatsapp.WhatsAppStickerLauncher;
import id.my.sinanonym.stickerwa.whatsapp.WhitelistCheck;

public class StickerPackDetailsActivity extends BaseActivity {

    /**
     * Do not change below values of below 3 lines as this is also used by WhatsApp
     */
    public static final String EXTRA_STICKER_PACK_ID = WhatsAppStickerLauncher.EXTRA_STICKER_PACK_ID;
    public static final String EXTRA_STICKER_PACK_AUTHORITY = WhatsAppStickerLauncher.EXTRA_STICKER_PACK_AUTHORITY;
    public static final String EXTRA_STICKER_PACK_NAME = WhatsAppStickerLauncher.EXTRA_STICKER_PACK_NAME;

    public static final String EXTRA_STICKER_PACK_WEBSITE = "sticker_pack_website";
    public static final String EXTRA_STICKER_PACK_EMAIL = "sticker_pack_email";
    public static final String EXTRA_STICKER_PACK_PRIVACY_POLICY = "sticker_pack_privacy_policy";
    public static final String EXTRA_STICKER_PACK_LICENSE_AGREEMENT = "sticker_pack_license_agreement";
    public static final String EXTRA_STICKER_PACK_TRAY_ICON = "sticker_pack_tray_icon";
    public static final String EXTRA_SHOW_UP_BUTTON = "show_up_button";
    public static final String EXTRA_STICKER_PACK_DATA = "sticker_pack";

    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private StickerPreviewAdapter stickerPreviewAdapter;
    private int numColumns;
    private View addButton;
    private View alreadyAddedText;
    private StickerPack stickerPack;
    private View divider;
    @Nullable
    private Future<?> whitelistTaskFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }
        boolean showUpButton = getIntent().getBooleanExtra(EXTRA_SHOW_UP_BUTTON, false);
        stickerPack = getIntent().getParcelableExtra(EXTRA_STICKER_PACK_DATA);
        TextView packNameTextView = findViewById(R.id.pack_name);
        TextView packPublisherTextView = findViewById(R.id.author);
        ImageView packTrayIcon = findViewById(R.id.tray_image);
        TextView packSizeTextView = findViewById(R.id.pack_size);
        SimpleDraweeView expandedStickerView = findViewById(R.id.sticker_details_expanded_sticker);

        addButton = findViewById(R.id.add_to_whatsapp_button);
        alreadyAddedText = findViewById(R.id.already_added_text);
        layoutManager = new GridLayoutManager(this, 1);
        recyclerView = findViewById(R.id.sticker_list);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(pageLayoutListener);
        recyclerView.addOnScrollListener(dividerScrollListener);
        divider = findViewById(R.id.divider);
        if (stickerPreviewAdapter == null) {
            stickerPreviewAdapter = new StickerPreviewAdapter(
                    getLayoutInflater(),
                    R.drawable.sticker_error,
                    getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size),
                    getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_padding),
                    stickerPack,
                    expandedStickerView
            );
            recyclerView.setAdapter(stickerPreviewAdapter);
        }
        packNameTextView.setText(stickerPack.name);
        packPublisherTextView.setText(stickerPack.publisher);
        packTrayIcon.setImageURI(StickerRepository.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile));
        packSizeTextView.setText(Formatter.formatShortFileSize(this, stickerPack.getTotalSize()));
        addButton.setOnClickListener(v -> WhatsAppStickerLauncher.addStickerPackToWhatsApp(this, stickerPack.identifier, stickerPack.name));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(showUpButton);
            getSupportActionBar().setTitle(showUpButton
                    ? getResources().getString(R.string.title_activity_sticker_pack_details_multiple_pack)
                    : getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, 1));
        }
        findViewById(R.id.sticker_pack_animation_indicator).setVisibility(stickerPack.animatedStickerPack ? View.VISIBLE : View.GONE);
    }

    private void launchInfoActivity(String publisherWebsite, String publisherEmail, String privacyPolicyWebsite, String licenseAgreementWebsite, String trayIconUriString) {
        Intent intent = new Intent(StickerPackDetailsActivity.this, StickerPackInfoActivity.class);
        intent.putExtra(EXTRA_STICKER_PACK_ID, stickerPack.identifier);
        intent.putExtra(EXTRA_STICKER_PACK_WEBSITE, publisherWebsite);
        intent.putExtra(EXTRA_STICKER_PACK_EMAIL, publisherEmail);
        intent.putExtra(EXTRA_STICKER_PACK_PRIVACY_POLICY, privacyPolicyWebsite);
        intent.putExtra(EXTRA_STICKER_PACK_LICENSE_AGREEMENT, licenseAgreementWebsite);
        intent.putExtra(EXTRA_STICKER_PACK_TRAY_ICON, trayIconUriString);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WhatsAppStickerLauncher.handleActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        MenuItem submitItem = menu.findItem(R.id.action_submit_to_author);
        if (submitItem != null) {
            submitItem.setVisible(stickerPack != null && stickerPack.isCustom());
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_submit_to_author && stickerPack != null) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.submit_to_author_title)
                    .setMessage(R.string.submit_to_author_confirm_msg)
                    .setPositiveButton(R.string.dialog_btn_submit, (dialog, which) -> {
                        StickerPackExporter.launchSubmissionEmail(this, stickerPack);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_info && stickerPack != null) {
            Uri trayIconUri = StickerRepository.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile);
            launchInfoActivity(
                    stickerPack.publisherWebsite,
                    stickerPack.publisherEmail,
                    stickerPack.privacyPolicyWebsite,
                    stickerPack.licenseAgreementWebsite,
                    trayIconUri.toString()
            );
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ViewTreeObserver.OnGlobalLayoutListener pageLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            int cols = recyclerView.getWidth() / recyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size);
            setNumColumns(Math.max(cols, 1));
        }
    };

    private void setNumColumns(int numColumns) {
        if (this.numColumns != numColumns) {
            layoutManager.setSpanCount(numColumns);
            this.numColumns = numColumns;
            if (stickerPreviewAdapter != null) {
                stickerPreviewAdapter.notifyDataSetChanged();
            }
        }
    }

    private final RecyclerView.OnScrollListener dividerScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull final RecyclerView recyclerView, final int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            updateDivider(recyclerView);
        }

        @Override
        public void onScrolled(@NonNull final RecyclerView recyclerView, final int dx, final int dy) {
            super.onScrolled(recyclerView, dx, dy);
            updateDivider(recyclerView);
        }

        private void updateDivider(RecyclerView recyclerView) {
            boolean showDivider = recyclerView.computeVerticalScrollOffset() > 0;
            if (divider != null) {
                divider.setVisibility(showDivider ? View.VISIBLE : View.INVISIBLE);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (stickerPack != null) {
            final Context appContext = getApplicationContext();
            final String packId = stickerPack.identifier;
            whitelistTaskFuture = AppExecutors.execute(
                    () -> WhitelistCheck.isWhitelisted(appContext, packId),
                    this::updateAddUI
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppExecutors.cancel(whitelistTaskFuture);
    }

    private void updateAddUI(Boolean isWhitelisted) {
        if (Boolean.TRUE.equals(isWhitelisted)) {
            addButton.setVisibility(View.GONE);
            alreadyAddedText.setVisibility(View.VISIBLE);
            findViewById(R.id.sticker_pack_details_tap_to_preview).setVisibility(View.GONE);
        } else {
            addButton.setVisibility(View.VISIBLE);
            alreadyAddedText.setVisibility(View.GONE);
            findViewById(R.id.sticker_pack_details_tap_to_preview).setVisibility(View.VISIBLE);
        }
    }
}
