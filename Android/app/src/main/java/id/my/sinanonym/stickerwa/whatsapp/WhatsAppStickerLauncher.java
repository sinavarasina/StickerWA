/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.whatsapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import id.my.sinanonym.stickerwa.BuildConfig;
import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;

/**
 * Helper class encapsulating the WhatsApp Sticker Pack intent launching and result handling.
 * Replaces the legacy AddStickerPackActivity inheritance hierarchy with clean composition.
 */
public final class WhatsAppStickerLauncher {
    public static final int REQUEST_CODE_ADD_PACK = 200;
    public static final String ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK";

    /**
     * Do not change these 3 constants as they are part of the WhatsApp Intent contract.
     */
    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";

    private static final String TAG = "WhatsAppStickerLauncher";

    private WhatsAppStickerLauncher() {}

    public static void addStickerPackToWhatsApp(@NonNull AppCompatActivity activity, @NonNull String identifier, @NonNull String stickerPackName) {
        try {
            PackageManager pm = activity.getPackageManager();
            if (!WhitelistCheck.isWhatsAppConsumerAppInstalled(pm) && !WhitelistCheck.isWhatsAppSmbAppInstalled(pm)) {
                Toast.makeText(activity, R.string.add_pack_fail_prompt_update_whatsapp, Toast.LENGTH_LONG).show();
                return;
            }
            final boolean whitelistedConsumer = WhitelistCheck.isStickerPackWhitelistedInWhatsAppConsumer(activity, identifier);
            final boolean whitelistedSmb = WhitelistCheck.isStickerPackWhitelistedInWhatsAppSmb(activity, identifier);
            if (!whitelistedConsumer && !whitelistedSmb) {
                launchIntentToAddPackToChooser(activity, identifier, stickerPackName);
            } else if (!whitelistedConsumer) {
                launchIntentToAddPackToSpecificPackage(activity, identifier, stickerPackName, WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME);
            } else if (!whitelistedSmb) {
                launchIntentToAddPackToSpecificPackage(activity, identifier, stickerPackName, WhitelistCheck.SMB_WHATSAPP_PACKAGE_NAME);
            } else {
                Toast.makeText(activity, R.string.add_pack_fail_prompt_update_whatsapp, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "error adding sticker pack to WhatsApp", e);
            Toast.makeText(activity, R.string.add_pack_fail_prompt_update_whatsapp, Toast.LENGTH_LONG).show();
        }
    }

    private static void launchIntentToAddPackToSpecificPackage(
            @NonNull AppCompatActivity activity,
            @NonNull String identifier,
            @NonNull String stickerPackName,
            @NonNull String whatsappPackageName) {
        Intent intent = createIntentToAddStickerPack(identifier, stickerPackName);
        intent.setPackage(whatsappPackageName);
        try {
            activity.startActivityForResult(intent, REQUEST_CODE_ADD_PACK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.add_pack_fail_prompt_update_whatsapp, Toast.LENGTH_LONG).show();
        }
    }

    private static void launchIntentToAddPackToChooser(
            @NonNull AppCompatActivity activity,
            @NonNull String identifier,
            @NonNull String stickerPackName) {
        Intent intent = createIntentToAddStickerPack(identifier, stickerPackName);
        try {
            activity.startActivityForResult(Intent.createChooser(intent, activity.getString(R.string.add_to_whatsapp)), REQUEST_CODE_ADD_PACK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.add_pack_fail_prompt_update_whatsapp, Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private static Intent createIntentToAddStickerPack(@NonNull String identifier, @NonNull String stickerPackName) {
        Intent intent = new Intent();
        intent.setAction(ACTION_ENABLE_STICKER_PACK);
        intent.putExtra(EXTRA_STICKER_PACK_ID, identifier);
        intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra(EXTRA_STICKER_PACK_NAME, stickerPackName);
        return intent;
    }

    public static void handleActivityResult(
            @NonNull AppCompatActivity activity,
            int requestCode,
            int resultCode,
            @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_ADD_PACK && resultCode == Activity.RESULT_CANCELED) {
            if (data != null) {
                final String validationError = data.getStringExtra("validation_error");
                if (validationError != null) {
                    if (BuildConfig.DEBUG) {
                        BaseActivity.MessageDialogFragment.newInstance(R.string.title_validation_error, validationError)
                                .show(activity.getSupportFragmentManager(), "validation error");
                    }
                    Log.e(TAG, "Validation failed:" + validationError);
                }
            } else {
                new StickerPackNotAddedMessageFragment().show(activity.getSupportFragmentManager(), "sticker_pack_not_added");
            }
        }
    }

    public static final class StickerPackNotAddedMessageFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.add_pack_fail_prompt_update_whatsapp)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dismiss())
                    .setNeutralButton(R.string.add_pack_fail_prompt_update_play_link, (dialog, which) -> launchWhatsAppPlayStorePage());

            return dialogBuilder.create();
        }

        private void launchWhatsAppPlayStorePage() {
            if (getActivity() != null) {
                final PackageManager packageManager = getActivity().getPackageManager();
                final boolean whatsAppInstalled = WhitelistCheck.isPackageInstalled(WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME, packageManager);
                final boolean smbAppInstalled = WhitelistCheck.isPackageInstalled(WhitelistCheck.SMB_WHATSAPP_PACKAGE_NAME, packageManager);
                final String playPackageLinkPrefix = "http://play.google.com/store/apps/details?id=";
                if (whatsAppInstalled && smbAppInstalled) {
                    launchPlayStoreWithUri("https://play.google.com/store/apps/developer?id=WhatsApp+LLC");
                } else if (whatsAppInstalled) {
                    launchPlayStoreWithUri(playPackageLinkPrefix + WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME);
                } else if (smbAppInstalled) {
                    launchPlayStoreWithUri(playPackageLinkPrefix + WhitelistCheck.SMB_WHATSAPP_PACKAGE_NAME);
                }
            }
        }

        private void launchPlayStoreWithUri(String uriString) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(uriString));
            intent.setPackage("com.android.vending");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.cannot_find_play_store, Toast.LENGTH_LONG).show();
            }
        }
    }
}
