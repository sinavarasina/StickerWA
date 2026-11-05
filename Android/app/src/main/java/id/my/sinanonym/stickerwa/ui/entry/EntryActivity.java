/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.ui.entry;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.concurrent.Future;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;
import id.my.sinanonym.stickerwa.ui.details.StickerPackDetailsActivity;
import id.my.sinanonym.stickerwa.ui.list.StickerPackListActivity;

public class EntryActivity extends BaseActivity {
    private static final String TAG = "EntryActivity";
    private View progressBar;
    @Nullable
    private Future<?> loadTaskFuture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);
        overridePendingTransition(0, 0);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        progressBar = findViewById(R.id.entry_activity_progress);
        final Context appContext = getApplicationContext();
        loadTaskFuture = AppExecutors.execute(
                () -> StickerRepository.fetchStickerPacks(appContext),
                this::showStickerPack,
                error -> showErrorMessage(error.getMessage())
        );
    }

    private void showStickerPack(ArrayList<StickerPack> stickerPackList) {
        progressBar.setVisibility(View.GONE);
        final Intent intent = new Intent(this, StickerPackListActivity.class);
        intent.putParcelableArrayListExtra(StickerPackListActivity.EXTRA_STICKER_PACK_LIST_DATA, stickerPackList);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private void showErrorMessage(String errorMessage) {
        progressBar.setVisibility(View.GONE);
        Log.e(TAG, "error fetching sticker packs, " + errorMessage);
        final TextView errorMessageTV = findViewById(R.id.error_message);
        errorMessageTV.setText(getString(R.string.error_message, errorMessage));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppExecutors.cancel(loadTaskFuture);
    }
}
